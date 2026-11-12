package com.target.engine

import kotlin.math.max

/**
 * Vinted no longer exposes upload times, but listing ids are allocated sequentially. The clock learns how many ids
 * Vinted allocates per second from (time, highest id seen) samples and converts an id into an approximate upload time.
 */
class IdClock(samples: List<Pair<Long, Long>> = emptyList()) {
    private val samples = ArrayList(samples.sortedBy { it.first })

    fun snapshot(): List<Pair<Long, Long>> = samples.toList()

    /** Records the highest id seen at [nowSec]. Keeps a bounded, time-ordered history. */
    fun record(nowSec: Long, maxId: Long) {
        if (maxId <= 0) return
        val last = samples.lastOrNull()
        if (last != null && maxId <= last.second) return // older search: no new information
        samples += nowSec to maxId
        while (samples.size > 60) samples.removeAt(0)
    }

    /** Ids allocated per second, learnt from samples at least 10 minutes apart. */
    fun rate(): Double {
        if (samples.size < 2) return DEFAULT_RATE
        val newest = samples.last()
        val oldest = samples.firstOrNull { newest.first - it.first >= 600 } ?: return DEFAULT_RATE
        if (oldest === newest) return DEFAULT_RATE
        val r = (newest.second - oldest.second).toDouble() / (newest.first - oldest.first)
        return r.coerceIn(2.0, 2_000.0)
    }

    /** Approximate upload time of [id], given that [refMaxId] was the newest listing at [nowSec]. */
    fun estimate(id: Long, refMaxId: Long, nowSec: Long): Long {
        val behind = max(0L, refMaxId - id)
        return nowSec - (behind / rate()).toLong()
    }

    /** Fills missing upload times (marking them as estimated). */
    fun annotate(listings: Collection<Listing>, nowSec: Long): List<Listing> {
        val ref = listings.maxOfOrNull { it.id } ?: return listings.toList()
        return listings.map { if (it.createdAt != null) it else it.copy(createdAt = estimate(it.id, ref, nowSec), ageEstimated = true) }
    }

    companion object { const val DEFAULT_RATE = 30.0 }
}
