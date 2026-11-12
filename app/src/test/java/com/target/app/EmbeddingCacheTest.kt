package com.target.app

import androidx.test.core.app.ApplicationProvider
import com.target.app.vision.EmbeddingCache
import com.target.engine.TasteModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmbeddingCacheTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun vec(seed: Int) = TasteModel.normalize(FloatArray(512) { Random(seed * 1000 + it).nextFloat() - 0.5f })

    @Test fun roundTripAndPersistence() {
        val a = EmbeddingCache(ctx, maxEntries = 100).apply { use("MobileCLIP-S2") }
        val v = vec(1)
        a.put(42L, v)
        assertTrue(TasteModel.cosine(a.get(42L)!!, v) > 0.999)
        // a new instance (app restart) reads it back from disk
        val b = EmbeddingCache(ctx, maxEntries = 100).apply { use("MobileCLIP-S2") }
        assertTrue(TasteModel.cosine(b.get(42L)!!, v) > 0.999)
        // another model never reuses those embeddings
        val c = EmbeddingCache(ctx).apply { use("MobileCLIP-S0") }
        assertNull(c.get(42L))
    }

    @Test fun evictsAndCompacts() {
        val cache = EmbeddingCache(ctx, maxEntries = 50).apply { use("test-model") }
        for (i in 0 until 200) cache.put(i.toLong(), vec(i))
        assertNull(cache.get(0L))
        assertTrue(TasteModel.cosine(cache.get(199L)!!, vec(199)) > 0.999)
        val reloaded = EmbeddingCache(ctx, maxEntries = 50).apply { use("test-model") }
        assertEquals(true, reloaded.contains(199L))
    }
}
