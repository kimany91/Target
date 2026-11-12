package com.target.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import com.target.app.data.DealStore
import com.target.app.data.ProfileStore
import com.target.app.data.Settings
import com.target.app.deals.DealWorker
import com.target.app.search.SearchEngine
import com.target.app.update.Updater
import com.target.app.vinted.VintedClient
import com.target.app.vision.Embedder
import com.target.app.vision.ImageAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (small app, no DI framework needed). */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = Settings(app)
    val profiles = ProfileStore(app)
    val deals = DealStore(app)
    val client by lazy { VintedClient(app) }
    val embedder by lazy { Embedder(app) }
    val analyzer by lazy { ImageAnalyzer(app, embedder) }
    val search by lazy { SearchEngine(client, settings, embedder) }
    val updater by lazy { Updater(app, client.http) }
}

class TargetApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DealWorker.createChannel(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("updates", getString(R.string.channel_updates), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_updates_desc)
            }
        )
        val s = container.settings.value
        DealWorker.schedule(this, s.scanMinutes, s.dealsEnabled)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()
}
