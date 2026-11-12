package com.target.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.target.app.BuildConfig
import com.target.engine.ReleaseInfo
import com.target.engine.Releases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: String?) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val info: ReleaseInfo) : UpdateState
    data class Installing(val info: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-update from the public GitHub releases of the project.
 * Security: HTTPS only, GitHub hosts only, optional SHA-256 check, and the downloaded APK must have the same package
 * name and the same signing certificate as the installed app (Android enforces it too) before it is handed to
 * PackageInstaller.
 */
class Updater(private val context: Context, private val http: OkHttpClient) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    private val mutex = Mutex()
    val currentVersion: String = BuildConfig.VERSION_NAME

    fun setState(s: UpdateState) { _state.value = s }

    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Checking
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val body = runCatching { http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
        val info = body?.let { Releases.parseLatest(it) }
        when {
            body == null -> { _state.value = UpdateState.Failed("Impossible de vérifier les mises à jour"); null }
            info != null && Releases.isNewer(currentVersion.removeSuffix("-debug"), info) -> { _state.value = UpdateState.Available(info); info }
            else -> { _state.value = UpdateState.UpToDate(info?.version?.toString()); null }
        }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Check → download → verify → install. Safe to call at every start. */
    suspend fun checkAndInstall(autoInstall: Boolean) = mutex.withLock {
        val info = check() ?: return@withLock
        if (autoInstall) install(info)
    }

    suspend fun install(info: ReleaseInfo) {
        if (!canInstall()) { _state.value = UpdateState.NeedsPermission(info); return }
        val apk = runCatching { download(info) }.getOrElse {
            _state.value = UpdateState.Failed(it.message ?: "Téléchargement impossible"); return
        }
        val problem = verify(apk, info)
        if (problem != null) { apk.delete(); _state.value = UpdateState.Failed(problem); return }
        _state.value = UpdateState.Installing(info)
        runCatching { commit(apk) }.onFailure { _state.value = UpdateState.Failed("Installation impossible : ${it.message}") }
    }

    private suspend fun download(info: ReleaseInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Target-${info.version}.apk")
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("Téléchargement refusé (${resp.code})")
            if (!resp.request.url.isHttps) error("Connexion non sécurisée")
            val body = resp.body ?: error("Fichier vide")
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize ?: -1L
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastP = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val p = (read * 100 / total).toInt()
                            if (p != lastP) { lastP = p; _state.value = UpdateState.Downloading(info, p / 100f) }
                        }
                    }
                }
            }
        }
        out
    }

    private suspend fun verify(apk: File, info: ReleaseInfo): String? = withContext(Dispatchers.IO) {
        info.sha256Url?.let { url ->
            val expected = runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string()?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase() }
            }.getOrNull()
            if (expected != null && expected.length == 64 && expected != sha256(apk)) return@withContext "Fichier corrompu (empreinte SHA-256 différente)"
        }
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: return@withContext "APK illisible"
        if (archive.packageName != context.packageName) return@withContext "APK d'une autre application"
        val installed = pm.getPackageInfo(context.packageName, signingFlag())
        if (versionCode(archive) <= versionCode(installed)) return@withContext "Cette version est déjà installée"
        if (signerDigests(archive).isEmpty() || signerDigests(archive) != signerDigests(installed)) return@withContext "Signature différente : mise à jour refusée"
        null
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 26) setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    private fun signingFlag(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(signingFlag().toLong()))
        else @Suppress("DEPRECATION") pm.getPackageArchiveInfo(apk.absolutePath, signingFlag())

    @Suppress("DEPRECATION")
    private fun versionCode(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    /** SHA-256 of the current signing certificate(s). */
    @Suppress("DEPRECATION")
    private fun signerDigests(p: PackageInfo): Set<String> {
        if (Build.VERSION.SDK_INT >= 28) {
            val si = p.signingInfo ?: return emptySet()
            val certs = if (si.hasMultipleSigners()) si.apkContentsSigners.toList() else listOfNotNull(si.signingCertificateHistory?.lastOrNull())
            return certs.map { sha256(it.toByteArray()) }.toSet()
        }
        return p.signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    companion object {
        fun sha256(file: File): String = file.inputStream().use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            md.digest().joinToString("") { "%02x".format(it) }
        }
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
