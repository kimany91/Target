package com.target.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the report of the last crash so that it can be shown (and copied) in Settings → Diagnostic:
 *  - Kotlin/Java crashes: stack trace written by the default uncaught-exception handler;
 *  - native crashes (vision runtime, GPU/NPU drivers) and system kills: read at the next start from Android's
 *    record of why the process ended (Android 11+).
 */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")
    private fun date(t: Long) = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(t))

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                file(app).writeText("Plantage ${date(System.currentTimeMillis())} · v${BuildConfig.VERSION_NAME} · ${Build.MODEL} Android ${Build.VERSION.RELEASE}\nThread ${thread.name}\n$sw".take(12_000))
            }
            previous?.uncaughtException(thread, e)
        }
        recordNativeExit(app)
    }

    private fun recordNativeExit(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val am = c.getSystemService(ActivityManager::class.java)
            val last = am.getHistoricalProcessExitReasons(c.packageName, 0, 1).firstOrNull() ?: return
            val prefs = c.getSharedPreferences("crashlog", Context.MODE_PRIVATE)
            if (prefs.getLong("seen", 0) == last.timestamp) return
            prefs.edit().putLong("seen", last.timestamp).apply()
            val kind = when (last.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Plantage natif"
                ApplicationExitInfo.REASON_ANR -> "Appli figée (ANR)"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "Fermée par manque de mémoire"
                ApplicationExitInfo.REASON_CRASH -> return   // already written by the handler with its stack trace
                else -> return
            }
            file(c).writeText("$kind ${date(last.timestamp)} · v${BuildConfig.VERSION_NAME} · ${Build.MODEL} Android ${Build.VERSION.RELEASE}\n" +
                "${last.description ?: ""} (pss ${last.pss / 1024} Mo)")
        }
    }

    fun last(c: Context): String? = file(c).takeIf { it.exists() }?.readText()
    fun clear(c: Context) { file(c).delete() }
}
