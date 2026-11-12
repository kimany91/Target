package com.target.app.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
