package com.target.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class DealsFile(val deals: List<StoredDeal> = emptyList(), val notified: List<Long> = emptyList(), val policy: Int = 0)

/** Deals found by the background scanner (most recent first) + ids already notified. */
class DealStore(context: Context) {
    private val file = JsonFile(File(context.filesDir, "deals.json"), DealsFile.serializer()) { DealsFile() }
    private var data = file.read().let {
        // deals found by an older, looser policy are dropped (they no longer qualify)
        if (it.policy < POLICY) it.copy(deals = emptyList(), policy = POLICY).also { d -> file.write(d) } else it
    }
    private val _deals = MutableStateFlow(data.deals)
    val deals: StateFlow<List<StoredDeal>> = _deals

    @Synchronized
    fun isNotified(id: Long) = id in data.notified

    /** Adds new deals; returns those that were never seen before. */
    @Synchronized
    fun add(newDeals: List<StoredDeal>): List<StoredDeal> {
        val known = data.deals.map { it.ranked.listing.id }.toSet() + data.notified
        val fresh = newDeals.filter { it.ranked.listing.id !in known }.distinctBy { it.ranked.listing.id }
        if (fresh.isEmpty()) return emptyList()
        data = data.copy(
            policy = POLICY,
            deals = (fresh + data.deals).take(200),
            notified = (data.notified + fresh.map { it.ranked.listing.id }).takeLast(2000),
        )
        file.write(data)
        _deals.value = data.deals
        return fresh
    }

    @Synchronized
    fun remove(id: Long) {
        data = data.copy(deals = data.deals.filterNot { it.ranked.listing.id == id })
        file.write(data); _deals.value = data.deals
    }

    @Synchronized
    fun clear() {
        data = data.copy(deals = emptyList())
        file.write(data); _deals.value = data.deals
    }

    fun find(id: Long): StoredDeal? = _deals.value.firstOrNull { it.ranked.listing.id == id }

    companion object { const val POLICY = 2 }
}
