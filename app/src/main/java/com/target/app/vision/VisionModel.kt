package com.target.app.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.target.engine.PhotoSignals
import com.target.engine.TasteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest

sealed interface VisionState {
    data object Missing : VisionState
    data class Downloading(val model: String, val progress: Float) : VisionState
    data class Ready(val model: String) : VisionState
    data class Failed(val message: String) : VisionState
}

/**
 * On-device image understanding with Apple's MobileCLIP (S2, with S0 as a fallback for phones that cannot run it).
 * The vision encoder (~72 MB) is downloaded once from a pinned Hugging Face revision and checked against its SHA-256;
 * the texts it is compared with (categories, beauty probes, styles) were encoded offline (assets/taste_*.json).
 */
class VisionModel(private val context: Context, private val http: OkHttpClient, private val fast: () -> Boolean = { false }) {
    private val _state = MutableStateFlow<VisionState>(VisionState.Missing)
    val state: StateFlow<VisionState> = _state
    private val env by lazy { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null
    var taste: TasteModel? = null
        private set
    private val loadLock = Mutex()
    private val runLock = Mutex()
    private val dir = File(context.filesDir, "models").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("vision", Context.MODE_PRIVATE)

    val ready: Boolean get() = session != null && taste != null
    val modelName: String? get() = taste?.name

    private fun assetModel(variant: String) = TasteModel.parse(context.assets.open("taste_$variant.json").use { it.readBytes().decodeToString() })

    /** Loads (and downloads on first use) the best model this phone can run. Safe to call repeatedly. */
    suspend fun ensure(): Boolean = loadLock.withLock {
        if (ready) return@withLock true
        val order = if (fast() || prefs.getBoolean("s2_unsupported", false)) listOf("s0") else listOf("s2", "s0")
        for (variant in order) {
            val tm = runCatching { assetModel(variant) }.getOrNull() ?: continue
            val file = File(dir, "${tm.sha256}.onnx")
            if (!file.exists() || file.length() != tm.bytes) {
                val ok = runCatching { download(tm, file) }.onFailure { _state.value = VisionState.Failed("Téléchargement du modèle impossible : ${it.message}") }.isSuccess
                if (!ok) return@withLock false
            }
            val s = withContext(Dispatchers.Default) { fastestSession(file, tm.inputSize) }
            if (s == null) {
                if (variant == "s2") prefs.edit().putBoolean("s2_unsupported", true).apply()
                file.delete()
                continue
            }
            session = s
            taste = tm
            // keep only the model in use
            dir.listFiles()?.filter { it.name != file.name && !it.name.endsWith(".part") }?.forEach { it.delete() }
            _state.value = VisionState.Ready(tm.name)
            return@withLock true
        }
        if (_state.value !is VisionState.Failed) _state.value = VisionState.Failed("Modèle de vision non supporté par ce téléphone")
        false
    }

    /** Drops the current model (e.g. after switching between precise and fast analysis). */
    suspend fun reset() = loadLock.withLock {
        session?.close(); session = null; taste = null
        _state.value = VisionState.Missing
    }

    /** Execution backend chosen for this phone (shown in Settings). */
    var backend: String = "CPU"
        private set

    private fun options(kind: String): OrtSession.SessionOptions = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
        runCatching { addConfigEntry("session.intra_op.allow_spinning", "1") }
        when (kind) {
            "XNNPACK" -> addXnnpack(mapOf("intra_op_num_threads" to Runtime.getRuntime().availableProcessors().coerceIn(2, 6).toString()))
            "NNAPI" -> addNnapi(java.util.EnumSet.of(ai.onnxruntime.providers.NNAPIFlags.USE_FP16))
        }
    }

    /**
     * Creates the session on the fastest backend of this phone. CPU and XNNPACK are timed on a batch; XNNPACK must
     * give the same embeddings as the CPU (cosine ≥ 0.99). NNAPI is not tried: it is deprecated since Android 15 and
     * its drivers can kill the app natively (crash on search reported on a Galaxy S25 Ultra).
     * A backend being tried is written down first (synchronously): if the app dies while trying it, the next start
     * finds the mark, never uses that backend again and stays on the CPU.
     */
    private fun fastestSession(file: File, size: Int): OrtSession? {
        val key = "backend_${file.name}"
        val bad = prefs.getStringSet("bad_backends", emptySet())!!.toMutableSet()
        prefs.getString("trying", null)?.let { crashed ->
            bad += crashed
            prefs.edit().putStringSet("bad_backends", bad).remove("trying").remove(key).commit()
        }
        prefs.getString(key, null)?.takeIf { it !in bad }?.let { known ->
            runCatching { env.createSession(file.absolutePath, options(known)) }.getOrNull()?.let { backend = known; verifyKind = known.takeIf { it != "CPU" }; return it }
        }
        val cpu = runCatching { env.createSession(file.absolutePath, options("CPU")) }.getOrNull() ?: return null
        val probe = FloatArray(4 * 3 * size * size) { ((it * 7919) % 1000) / 1000f }
        fun bench(s: OrtSession): Pair<Long, FloatArray>? = runCatching {
            run(s, probe, 4, size) // warm-up
            val t0 = System.nanoTime(); val out = run(s, probe, 4, size); (System.nanoTime() - t0) to out[0]
        }.getOrNull()
        val (cpuTime, ref) = bench(cpu) ?: return cpu
        var best: OrtSession = cpu; var bestTime = cpuTime; var bestKind = "CPU"
        for (kind in listOf("XNNPACK").filter { it !in bad }) {
            prefs.edit().putString("trying", kind).commit()
            val s = runCatching { env.createSession(file.absolutePath, options(kind)) }.getOrNull()
            val r = s?.let { bench(it) }
            prefs.edit().remove("trying").commit()
            if (s == null) continue
            if (r != null && TasteModel.cosine(r.second, ref) >= 0.99 && r.first < bestTime * 0.9) {
                if (best !== cpu) best.close()
                best = s; bestTime = r.first; bestKind = kind
            } else s.close()
        }
        if (best !== cpu) cpu.close()
        backend = bestKind
        prefs.edit().putString(key, bestKind).apply()
        return best
    }

    /** Non-CPU backend whose first real batch has not run yet (guarded like the benchmark). */
    private var verifyKind: String? = null

    /** Runs a batch of [n] preprocessed images (CHW floats, concatenated). */
    private fun run(s: OrtSession, data: FloatArray, n: Int, size: Int): List<FloatArray> =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(n.toLong(), 3, size.toLong(), size.toLong())).use { t ->
            s.run(mapOf(s.inputNames.first() to t)).use { r ->
                @Suppress("UNCHECKED_CAST")
                (r[0].value as Array<FloatArray>).map { TasteModel.normalize(it) }
            }
        }

    /** Preprocessed input for [embedBatch] (can run in parallel, off the inference thread). */
    fun prepare(bitmap: Bitmap): FloatArray? {
        val size = taste?.inputSize ?: return null
        val buf = preprocess(bitmap, size)
        return FloatArray(3 * size * size).also { buf.get(it) }
    }

    /** Embeds several prepared images in one pass (much faster than one by one). */
    suspend fun embedBatch(inputs: List<FloatArray>): List<FloatArray>? {
        val s = session ?: return null
        val size = taste?.inputSize ?: return null
        if (inputs.isEmpty()) return emptyList()
        val data = FloatArray(inputs.size * 3 * size * size)
        inputs.forEachIndexed { i, a -> System.arraycopy(a, 0, data, i * a.size, a.size) }
        return runLock.withLock {
            withContext(Dispatchers.Default) {
                val v = verifyKind
                if (v != null) prefs.edit().putString("trying", v).commit()
                val out = runCatching { run(s, data, inputs.size, size) }.getOrNull()
                if (v != null) { prefs.edit().remove("trying").commit(); verifyKind = null }
                out
            }
        }
    }

    private suspend fun download(tm: TasteModel, out: File) = withContext(Dispatchers.IO) {
        val tmp = File(out.parentFile, out.name + ".part")
        _state.value = VisionState.Downloading(tm.name, 0f)
        http.newCall(Request.Builder().url(tm.modelUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("vide")
            val total = body.contentLength().takeIf { it > 0 } ?: tm.bytes
            val md = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(128 * 1024)
                    var read = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n); md.update(buf, 0, n); read += n
                        val p = (read * 100 / total).toInt()
                        if (p != last) { last = p; _state.value = VisionState.Downloading(tm.name, p / 100f) }
                    }
                }
            }
            val sha = md.digest().joinToString("") { "%02x".format(it) }
            if (sha != tm.sha256) { tmp.delete(); error("empreinte invalide") }
        }
        if (!tmp.renameTo(out)) error("écriture impossible")
    }

    /** Image embedding (L2-normalised), or null when the model is not available. */
    suspend fun embed(bitmap: Bitmap): FloatArray? {
        val s = session ?: return null
        val size = taste?.inputSize ?: 256
        val input = withContext(Dispatchers.Default) { preprocess(bitmap, size) }
        return runLock.withLock {
            withContext(Dispatchers.Default) {
                runCatching {
                    OnnxTensor.createTensor(env, input, longArrayOf(1, 3, size.toLong(), size.toLong())).use { t ->
                        s.run(mapOf(s.inputNames.first() to t)).use { r ->
                            @Suppress("UNCHECKED_CAST")
                            val out = (r[0].value as Array<FloatArray>)[0]
                            TasteModel.normalize(out)
                        }
                    }
                }.getOrNull()
            }
        }
    }

    fun analyze(embedding: FloatArray): PhotoSignals? = taste?.analyze(embedding)

    /** CLIP preprocessing: resize the short side to [size] (progressive bilinear), centre crop, scale to [0,1], CHW. */
    private fun preprocess(src: Bitmap, size: Int): FloatBuffer {
        var b = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        while (minOf(b.width, b.height) >= size * 2) b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        val scale = size.toFloat() / minOf(b.width, b.height)
        val w = maxOf(size, Math.round(b.width * scale)); val h = maxOf(size, Math.round(b.height * scale))
        b = Bitmap.createScaledBitmap(b, w, h, true)
        val crop = Bitmap.createBitmap(b, (w - size) / 2, (h - size) / 2, size, size)
        val px = IntArray(size * size)
        crop.getPixels(px, 0, size, 0, 0, size, size)
        val buf = FloatBuffer.allocate(3 * size * size)
        val plane = size * size
        for (i in 0 until plane) {
            val c = px[i]
            buf.put(i, ((c shr 16) and 0xFF) / 255f)
            buf.put(plane + i, ((c shr 8) and 0xFF) / 255f)
            buf.put(2 * plane + i, (c and 0xFF) / 255f)
        }
        buf.rewind()
        return buf
    }
}
