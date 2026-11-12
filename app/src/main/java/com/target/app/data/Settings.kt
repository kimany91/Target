package com.target.app.data

import android.content.Context
import com.target.engine.Sensitivity
import com.target.engine.VintedDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val dealsEnabled: Boolean = true,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    val scanMinutes: Int = 30,
    val home: VintedDomain = VintedDomain.FR,
    val extraDomains: Set<VintedDomain> = emptySet(),
    val haptics: Boolean = true,
    val autoUpdate: Boolean = true,
    val deepSearch: Boolean = true,
    val quietNight: Boolean = true,
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state
    val value: AppSettings get() = _state.value

    private fun load() = AppSettings(
        dealsEnabled = prefs.getBoolean("deals", true),
        sensitivity = runCatching { Sensitivity.valueOf(prefs.getString("sens", "NORMAL")!!) }.getOrDefault(Sensitivity.NORMAL),
        scanMinutes = prefs.getInt("scan", 30),
        home = VintedDomain.fromName(prefs.getString("home", "FR")),
        extraDomains = prefs.getStringSet("extra", emptySet())!!.mapNotNull { n -> VintedDomain.entries.firstOrNull { it.name == n } }.toSet(),
        haptics = prefs.getBoolean("haptics", true),
        autoUpdate = prefs.getBoolean("autoUpdate", true),
        deepSearch = prefs.getBoolean("deep", true),
        quietNight = prefs.getBoolean("quiet", true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        prefs.edit()
            .putBoolean("deals", s.dealsEnabled).putString("sens", s.sensitivity.name).putInt("scan", s.scanMinutes)
            .putString("home", s.home.name).putStringSet("extra", s.extraDomains.map { it.name }.toSet())
            .putBoolean("haptics", s.haptics).putBoolean("autoUpdate", s.autoUpdate).putBoolean("deep", s.deepSearch)
            .putBoolean("quiet", s.quietNight)
            .apply()
        _state.value = s
    }
}
