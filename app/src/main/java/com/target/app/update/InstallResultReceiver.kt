package com.target.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.target.app.TargetApp

/** Receives the PackageInstaller session result and hands it to the [Updater]. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? TargetApp ?: return
        val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
        app.container.updater.onSessionResult(
            intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            confirm,
        )
    }
}
