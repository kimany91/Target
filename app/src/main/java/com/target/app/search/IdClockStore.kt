package com.target.app.search

import android.content.Context
import com.target.engine.IdClock
import com.target.engine.Listing

/** Persists the id → time calibration used to estimate listing ages. */
class IdClockStore(context: Context) {
    private val prefs = context.getSharedPreferences("idclock", Context.MODE_PRIVATE)
    private val clock = IdClock(
        prefs.getString("s", "").orEmpty().split(',').mapNotNull { p ->
            val (t, id) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            (t.toLongOrNull() ?: return@mapNotNull null) to (id.toLongOrNull() ?: return@mapNotNull null)
        }
    )

    @Synchronized
    fun annotate(listings: Collection<Listing>, record: Boolean): List<Listing> {
        val now = System.currentTimeMillis() / 1000
        if (record) listings.maxOfOrNull { it.id }?.let {
            clock.record(now, it)
            prefs.edit().putString("s", clock.snapshot().joinToString(",") { (t, id) -> "$t:$id" }).apply()
        }
        return clock.annotate(listings, now)
    }
}
