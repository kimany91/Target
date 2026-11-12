package com.target.app.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** On-device visual embeddings (MobileNetV3) used to find listings that *look* like a reference photo. */
class Embedder(private val context: Context) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val thread = Dispatchers.Default.limitedParallelism(1)
    private var embedder: ImageEmbedder? = null
    private var failed = false

    private fun get(): ImageEmbedder? {
        if (embedder == null && !failed) {
            embedder = runCatching {
                ImageEmbedder.createFromOptions(
                    context,
                    ImageEmbedder.ImageEmbedderOptions.builder()
                        .setBaseOptions(BaseOptions.builder().setModelAssetPath("image_embedder.tflite").build())
                        .setL2Normalize(true)
                        .setQuantize(false)
                        .build()
                )
            }.onFailure { failed = true }.getOrNull()
        }
        return embedder
    }

    private val detector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE).build()
        )
    }

    /**
     * Embedding of the garment itself, like a visual search engine would do: the main object is detected and cropped
     * so that the background (bed, floor, hanger, mirror) does not drive the similarity.
     */
    suspend fun embedGarment(bitmap: Bitmap): FloatArray? {
        val crop = runCatching {
            val objects = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
            val box = objects.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }?.boundingBox
            if (box == null || box.width() * box.height() < bitmap.width * bitmap.height * 0.08) null
            else {
                val padX = (box.width() * 0.06).toInt(); val padY = (box.height() * 0.06).toInt()
                val l = (box.left - padX).coerceAtLeast(0); val t = (box.top - padY).coerceAtLeast(0)
                val r = (box.right + padX).coerceAtMost(bitmap.width); val b = (box.bottom + padY).coerceAtMost(bitmap.height)
                if (r - l < 32 || b - t < 32) null else Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
            }
        }.getOrNull() ?: centerCrop(bitmap, 0.85f)
        return embed(crop)
    }

    private fun centerCrop(b: Bitmap, f: Float): Bitmap {
        val w = (b.width * f).toInt().coerceAtLeast(1); val h = (b.height * f).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(b, (b.width - w) / 2, (b.height - h) / 2, w, h)
    }

    suspend fun embed(bitmap: Bitmap): FloatArray? = withContext(thread) {
        runCatching {
            val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            get()?.embed(BitmapImageBuilder(argb).build())?.embeddingResult()?.embeddings()?.firstOrNull()?.floatEmbedding()
        }.getOrNull()
    }

    companion object {
        fun cosine(a: FloatArray, b: FloatArray): Double {
            if (a.size != b.size || a.isEmpty()) return 0.0
            var dot = 0.0; var na = 0.0; var nb = 0.0
            for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            return if (na == 0.0 || nb == 0.0) 0.0 else dot / (sqrt(na) * sqrt(nb))
        }
    }
}
