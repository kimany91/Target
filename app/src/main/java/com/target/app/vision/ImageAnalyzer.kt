package com.target.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.target.app.data.ImageAnalysis
import com.target.engine.Brands
import com.target.engine.KeywordKind
import com.target.engine.Taste
import com.target.engine.QueryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * "Lens" pipeline, fully on-device (photos never leave the phone), built on the MobileCLIP model:
 *  - the whole photo and its upper / lower halves (outfit photos from Pinterest show several pieces) are embedded;
 *    each crop gives the kind of piece it shows and its colours (texts compared offline, tools/taste/lens.py);
 *  - material / pattern, visible logo, style and beauty come from the whole photo;
 *  - ML Kit OCR only confirms a brand written on the photo (a label, a printed logo).
 * Generic ML Kit labels ("Sleeve", "Pattern", "Jersey") are no longer used: they made the tags meaningless.
 */
class ImageAnalyzer(private val context: Context, private val vision: VisionModel) {
    private val ocr by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    companion object {
        /** Bumped when the analysis changes, so that saved refs are analysed again. */
        private const val VERSION = "lens3"
        fun version(modelName: String) = "$modelName|$VERSION"

        // thresholds checked on 141 real Vinted photos (tools/taste/lens.py)
        private const val PIECE_MIN = 0.30
        private const val COLOR_MIN = 0.25
        private val MATERIAL_MIN = mapOf("cable" to 0.8, "leather" to 0.75, "suede" to 0.75, "corduroy" to 0.85).withDefault { 0.7 }
        /** Body regions, in the order pieces are listed (the top of an outfit is usually the piece hunted for). */
        private val REGIONS = listOf("upper", "outer", "lower", "shoes", "acc", "other")
        private fun regionOf(piece: String) = when (if (piece == "joggers") "bottom" else Taste.FAMILY[piece]) {
            "knit", "sweat", "top" -> "upper"; "outer" -> "outer"; "bottom" -> "lower"; "shoes" -> "shoes"; "acc" -> "acc"; else -> "other"
        }
        /** A logo above this is a tag that is on; between [BRAND_SUGGEST] and this, a suggestion (≈ 85 % right at 0.7). */
        private const val BRAND_ON = 0.8
        private const val BRAND_SUGGEST = 0.55
    }

    /** Copies the picked image into private storage (downscaled JPEG) and returns the file. */
    suspend fun importImage(uri: Uri, dest: File): File = withContext(Dispatchers.IO) {
        val bmp = decode(uri, 1280)
        FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        dest
    }

    suspend fun analyze(file: File): ImageAnalysis = withContext(Dispatchers.Default) {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext ImageAnalysis()
        val text = runCatching { ocr.process(InputImage.fromBitmap(bmp, 0)).await().text }.getOrDefault("")
        val ocrBrands = if (text.isBlank()) emptyList() else
            QueryParser.parse(text.replace('\n', ' ')).recognized.filter { it.kind == KeywordKind.BRAND && !it.corrected }.map { it.id }.distinct()

        if (!vision.ensure()) return@withContext ImageAnalysis(brands = ocrBrands.take(2), ocrText = text.take(200))
        val taste = vision.taste ?: return@withContext ImageAnalysis(brands = ocrBrands.take(2))

        // crops: whole photo, and for a tall photo (a person, an outfit) the upper body without the head and the legs
        val w = bmp.width; val h = bmp.height
        val crops = buildList {
            add(bmp)
            if (h > w * 1.15) {
                add(Bitmap.createBitmap(bmp, (w * 0.1).toInt(), (h * 0.12).toInt(), (w * 0.8).toInt(), (h * 0.48).toInt()))
                add(Bitmap.createBitmap(bmp, (w * 0.1).toInt(), (h * 0.5).toInt(), (w * 0.8).toInt(), (h * 0.47).toInt()))
            }
        }
        val embs = vision.embedBatch(crops.mapNotNull { vision.prepare(it) })?.takeIf { it.size == crops.size && it.all { e -> e.isNotEmpty() } }
            ?: return@withContext ImageAnalysis(brands = ocrBrands.take(2), ocrText = text.take(200))
        val main = embs[0]
        val photo = taste.analyze(main)
        val scores = HashMap<String, Float>()

        // pieces: one per body region (upper, outer layer, legs, shoes, accessory), from the crop that sees it best.
        // An upper piece that the category softmax calls "sweatshirt" / "t-shirt" is a knit when the knitwear probe
        // says so (jacquard and cable knits on Pinterest outfits).
        data class Cand(val piece: String, val p: Double, val crop: Int)
        val best = HashMap<String, Cand>()
        embs.forEachIndexed { i, e ->
            val signals = if (i == 0) photo else taste.analyze(e)
            for ((cat, p) in signals.category.entries.sortedByDescending { it.value }.take(if (i == 0) 2 else 1)) {
                val region = regionOf(cat)
                val piece = if (region == "upper" && Taste.FAMILY[cat] in setOf("sweat", "top") && (signals.logits["knitwear"] ?: -9.0) >= 2.5) "knit" else cat
                if (p < if (region == "acc") 0.5 else PIECE_MIN) continue
                if ((best[region]?.p ?: 0.0) < p) best[region] = Cand(piece, p, i)
            }
        }
        val chosen = REGIONS.mapNotNull { best[it] }.take(3)
        val items = chosen.map { scores[it.piece] = it.p.toFloat(); it.piece }.distinct()
        val pieceCrop = chosen.associate { it.piece to it.crop }

        // colours of the main piece (its crop), then of the others
        val colors = LinkedHashSet<String>()
        for (piece in items) {
            for ((c, p) in taste.colors(embs[pieceCrop[piece] ?: 0]).take(2)) if (p >= COLOR_MIN) { colors += c; scores[c] = maxOf(scores[c] ?: 0f, p.toFloat()) }
        }
        val materials = taste.materials(main).filter { (m, p) -> p >= MATERIAL_MIN.getValue(m) }.take(2).map { (m, p) -> scores[m] = p.toFloat(); m }

        // brand: written on the photo (OCR) or a recognised logo
        val brands = ArrayList(ocrBrands.take(2)); val inactive = ArrayList<String>()
        ocrBrands.forEach { scores[it] = 1f }
        taste.brands(main).firstOrNull()?.let { (b, p) ->
            if (b != "none" && b !in brands && p >= BRAND_SUGGEST && Brands.byId(b) != null) {
                brands += b; scores[b] = p.toFloat()
                if (p < BRAND_ON || ocrBrands.isNotEmpty()) inactive += b
            }
        }

        // style and beauty (display; the style also helps the user pick keywords)
        val mean = photo.styles.values.average()
        val styles = photo.styles.entries.sortedByDescending { it.value }.take(2).filter { (it.value - mean) * 25 >= 0.35 }.map { it.key }
        val beauty = photo.beauty?.let { (5.0 + (it - 0.5) * 10.0).coerceIn(0.0, 10.0) }

        ImageAnalysis(
            items = items, materials = materials, colors = colors.take(3).toList(), brands = brands, ocrText = text.take(200),
            embedding = main.toList(), model = version(vision.modelName.orEmpty()),
            scores = scores, inactive = inactive, styles = styles, beauty = beauty,
            pieceEmbedding = items.firstOrNull()?.let { embs[pieceCrop[it] ?: 0] }?.toList().orEmpty(),
        )
    }

    suspend fun decode(uri: Uri, maxSide: Int): Bitmap = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
                val s = maxOf(info.size.width, info.size.height)
                if (s > maxSide) dec.setTargetSize(info.size.width * maxSide / s, info.size.height * maxSide / s)
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
            context.contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })!!
            }
        }
    }
}
