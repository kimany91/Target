package com.target.app.vision

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Persistent memory of listing photos already seen by the vision model, so that a photo is analysed once, ever
 * (new searches, background deal scans, after a restart). Embeddings are stored as int8 with one scale per vector
 * (≈ 520 bytes per listing; cosine error < 0.001), appended to one file per model and compacted when it grows.
 */
class EmbeddingCache(context: Context, private val maxEntries: Int = 20_000) {
    private val dir = File(context.filesDir, "embcache").apply { mkdirs() }
    private var model: String? = null
    private var file: File? = null
    private val map = object : LinkedHashMap<Long, Pair<Float, ByteArray>>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Pair<Float, ByteArray>>?) = size > maxEntries
    }
    private var appended = 0

    /** Selects the cache of a model (embeddings of different models are not comparable). */
    @Synchronized
    fun use(modelName: String) {
        if (modelName == model) return
        model = modelName
        map.clear(); appended = 0
        val f = File(dir, modelName.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".bin")
        file = f
        dir.listFiles()?.filter { it.name != f.name }?.forEach { it.delete() }
        if (!f.exists()) return
        runCatching {
            DataInputStream(f.inputStream().buffered()).use { inp ->
                while (inp.available() > 0) {
                    val id = inp.readLong(); val scale = inp.readFloat(); val n = inp.readShort().toInt()
                    val q = ByteArray(n); inp.readFully(q)
                    map[id] = scale to q
                }
            }
        }.onFailure { f.delete(); map.clear() }
    }

    @Synchronized
    fun get(id: Long): FloatArray? {
        val (scale, q) = map[id] ?: return null
        return FloatArray(q.size) { q[it] * scale }
    }

    @Synchronized
    fun contains(id: Long) = map.containsKey(id)

    @Synchronized
    fun put(id: Long, v: FloatArray) {
        val f = file ?: return
        if (v.isEmpty() || !v.all { it.isFinite() }) return
        val maxAbs = v.maxOf { abs(it) }.coerceAtLeast(1e-6f)
        val scale = maxAbs / 127f
        val q = ByteArray(v.size) { (v[it] / scale).roundToInt().coerceIn(-127, 127).toByte() }
        map[id] = scale to q
        runCatching {
            DataOutputStream(FileOutputStream(f, true).buffered()).use { out -> write(out, id, scale, q) }
        }
        if (++appended > maxEntries / 2) compact()
    }

    private fun write(out: DataOutputStream, id: Long, scale: Float, q: ByteArray) {
        out.writeLong(id); out.writeFloat(scale); out.writeShort(q.size); out.write(q)
    }

    /** Rewrites the file with the entries still in memory (drops evicted / duplicated records). */
    private fun compact() {
        val f = file ?: return
        val tmp = File(f.parentFile, f.name + ".tmp")
        runCatching {
            DataOutputStream(FileOutputStream(tmp).buffered()).use { out -> for ((id, e) in map) write(out, id, e.first, e.second) }
            tmp.renameTo(f)
        }
        appended = 0
    }
}
