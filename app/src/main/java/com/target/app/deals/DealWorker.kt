package com.target.app.deals

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.target.app.MainActivity
import com.target.app.R
import com.target.app.TargetApp
import com.target.app.data.StoredDeal
import com.target.engine.Lexicon
import kotlinx.coroutines.flow.last
import okhttp3.Request
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Periodically scans every Ref profile and notifies exceptional offers ("Deal"). */
class DealWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as TargetApp
        val c = app.container
        val s = c.settings.value
        if (!s.dealsEnabled) return Result.success()
        val found = ArrayList<StoredDeal>()
        val now = System.currentTimeMillis() / 1000
        for (profile in c.profiles.profiles.value.filter { it.dealsEnabled && (it.keywords.isNotEmpty() || it.images.isNotEmpty()) }) {
            val state = runCatching { c.search.searchProfile(profile, budget = 10, photoBudget = 40).last() }.getOrNull() ?: continue
            state.results
                .filter { it.deal?.isDeal == true && (it.listing.createdAt == null || now - it.listing.createdAt!! < 36 * 3600) }
                .take(5)
                .forEach { found += StoredDeal(it, profile.id, profile.name, System.currentTimeMillis()) }
        }
        val fresh = c.deals.add(found)
        val quiet = s.quietNight && LocalTime.now().let { it.hour >= 23 || it.hour < 8 }
        if (!quiet) fresh.take(3).forEach { notify(applicationContext, it, c.client.http) }
        return Result.success()
    }

    companion object {
        const val CHANNEL = "deals"
        private const val WORK = "deal-scan"

        fun schedule(context: Context, minutes: Int, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(WORK); return }
            val req = PeriodicWorkRequestBuilder<DealWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_deals), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_deals_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 40, 60, 40, 60, 120)
            })
        }

        fun notify(context: Context, deal: StoredDeal, http: okhttp3.OkHttpClient) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                android.os.Build.VERSION.SDK_INT >= 33) return
            val r = deal.ranked
            val l = r.listing
            val piece = r.matchedItem?.let { Lexicon.concept(it)?.label } ?: ""
            val title = "🔥 Deal · ${listOfNotNull(r.matchedBrand ?: l.brand, piece.ifEmpty { null }).joinToString(" ")}".trim()
            val text = "${fmt(l.price)} ${sym(l.currency)} au lieu de ~${fmt(r.expectedPrice)} € · ${l.condition.label}" + (l.size?.let { " · $it" } ?: "")
            val image: Bitmap? = l.photoUrl?.let { url ->
                runCatching {
                    http.newCall(Request.Builder().url(url).build()).execute().use { resp -> resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                }.getOrNull()
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_DEAL_ID, l.id)
            }
            val pi = PendingIntent.getActivity(context, l.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val b = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_target)
                .setColor(0xFFFF3D5A.toInt())
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(deal.profileName)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
            if (image != null) b.setLargeIcon(image).setStyle(NotificationCompat.BigPictureStyle().bigPicture(image).bigLargeIcon(null as Bitmap?).setSummaryText(text))
            else b.setStyle(NotificationCompat.BigTextStyle().bigText("${l.title}\n$text"))
            runCatching { NotificationManagerCompat.from(context).notify(l.id.toInt(), b.build()) }
        }

        fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.FRANCE, "%.2f", v)
        fun sym(cur: String) = when (cur) { "EUR" -> "€"; "GBP" -> "£"; "PLN" -> "zł"; else -> cur }
    }
}
