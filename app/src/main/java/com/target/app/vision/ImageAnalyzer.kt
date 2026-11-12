package com.target.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.palette.graphics.Palette
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.target.app.data.ImageAnalysis
import com.target.engine.ConceptKind
import com.target.engine.KeywordKind
import com.target.engine.Lexicon
import com.target.engine.QueryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * "Lens" pipeline, fully on-device (photos never leave the phone):
 *  - ML Kit image labelling → type of garment / material,
 *  - ML Kit OCR → brand printed on a label or logo,
 *  - Palette on the centre of the photo → dominant colours,
 *  - MobileNet embedding → visual fingerprint used to re-rank Vinted results by look.
 */
class ImageAnalyzer(private val context: Context, private val embedder: Embedder) {
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build()) }
    private val ocr by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Copies the picked image into private storage (downscaled JPEG) and returns the file. */
    suspend fun importImage(uri: Uri, dest: File): File = withContext(Dispatchers.IO) {
        val bmp = decode(uri, 1280)
        FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        dest
    }

    suspend fun analyze(file: File): ImageAnalysis = withContext(Dispatchers.Default) {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext ImageAnalysis()
        val input = InputImage.fromBitmap(bmp, 0)

        val labels = runCatching { labeler.process(input).await() }.getOrNull().orEmpty()
            .sortedByDescending { it.confidence }.map { it.text }
        val concepts = labels.flatMap { Lexicon.conceptsForLabel(it) }.distinct()

        val text = runCatching { ocr.process(input).await().text }.getOrDefault("")
        val brands = if (text.isBlank()) emptyList() else
            QueryParser.parse(text.replace('\n', ' ')).recognized.filter { it.kind == KeywordKind.BRAND && !it.corrected }.map { it.id }.distinct()

        val colors = dominantColors(bmp)
        val emb = embedder.embed(Bitmap.createScaledBitmap(bmp, 256, (256f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true))

        ImageAnalysis(
            labels = labels.take(8),
            items = concepts.filter { it.kind == ConceptKind.ITEM }.map { it.id }.take(2),
            materials = concepts.filter { it.kind == ConceptKind.MATERIAL || it.kind == ConceptKind.PATTERN }.map { it.id }.take(2),
            colors = colors,
            brands = brands.take(2),
            ocrText = text.take(200),
            embedding = emb?.toList().orEmpty(),
        )
    }

    /** Colours of the garment: Palette on the central area (the background is usually at the edges). */
    private fun dominantColors(bmp: Bitmap): List<String> {
        val w = bmp.width; val h = bmp.height
        val crop = Bitmap.createBitmap(bmp, (w * 0.2).toInt(), (h * 0.2).toInt(), (w * 0.6).toInt().coerceAtLeast(1), (h * 0.6).toInt().coerceAtLeast(1))
        val palette = Palette.from(crop).maximumColorCount(12).generate()
        val swatches = palette.swatches.sortedByDescending { it.population }
        val total = swatches.sumOf { it.population }.coerceAtLeast(1)
        return swatches.filter { it.population > total * 0.12 }
            .map { Lexicon.nearestColor(it.rgb and 0xFFFFFF).id }
            .distinct().take(2)
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
