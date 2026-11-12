package com.target.app.update

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.target.app.BuildConfig
import com.target.app.MainActivity
import com.target.app.R
import com.target.app.TargetApp

/**
 * Runs in the new version right after an update (MY_PACKAGE_REPLACED, a protected broadcast only the system sends).
 * Re-opens Target when Android allows it and always posts a "Target is up to date" notification to reopen it.
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as? TargetApp ?: return
        if (app.container.updater.takeRelaunch() == null) return
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(open) }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(context, 7, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, "updates")
            .setSmallIcon(R.drawable.ic_stat_target)
            .setColor(0xFFFF3D5A.toInt())
            .setContentTitle("Target est à jour ✓")
            .setContentText("Version ${BuildConfig.VERSION_NAME} installée · toucher pour ouvrir")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(4242, n) }
    }
}
