package com.target.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.target.app.TargetApp
import com.target.app.data.AppSettings
import com.target.app.data.ProfileStore
import com.target.app.data.RefImage
import com.target.app.deals.DealWorker
import com.target.app.search.SearchState
import com.target.app.update.UpdateState
import com.target.engine.Listing
import com.target.engine.QueryParser
import com.target.engine.RankedListing
import com.target.engine.Ranker
import com.target.engine.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab { REFS, SEARCH, DEALS, SETTINGS }

sealed interface NavEvent {
    data class OpenItem(val id: Long) : NavEvent
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TargetApp).container

    val profiles = c.profiles.profiles
    val activeId = c.profiles.activeId
    val active = combine(profiles, activeId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.profiles.active())
    val settings: StateFlow<AppSettings> = c.settings.state
    val deals = c.deals.deals
    val update: StateFlow<UpdateState> = c.updater.state
    val currentVersion = c.updater.currentVersion
    val updateLog = c.updater.log

    val tab = MutableStateFlow(Tab.REFS)
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SortMode.ATTRACTIVE)
    private val _search = MutableStateFlow<SearchState?>(null)
    val search: StateFlow<SearchState?> = _search
    val searchLabel = MutableStateFlow<String?>(null)
    val analyzing = MutableStateFlow<Set<String>>(emptySet())
    val vintedUser = MutableStateFlow<String?>(null)
    val loggedIn = MutableStateFlow(true)
    /** Asks the user to connect a Vinted account (at first launch and at every launch while not connected). */
    val connectPrompt = MutableStateFlow(false)
    val vintedLog get() = c.client.log
    val selfTest = MutableStateFlow<String?>(null)
    val nav = MutableSharedFlow<NavEvent>(extraBufferCapacity = 4)

    /** Listings shown in the UI, by id (for the detail screen). */
    private val cache = HashMap<Long, RankedListing>()
    private val _details = MutableStateFlow<Map<Long, Listing>>(emptyMap())
    val details: StateFlow<Map<Long, Listing>> = _details
    private var searchJob: Job? = null
    private var started = false

    // ---------- lifecycle ----------

    fun onAppStart() {
        if (started) return
        started = true
        viewModelScope.launch(Dispatchers.IO) {
            if (settings.value.autoUpdate) c.updater.checkAndInstall(autoInstall = true) else c.updater.check()
        }
        refreshVintedUser(promptIfMissing = true)
    }

    /** Coming back from the "install unknown apps" screen: resume a pending update. */
    fun onResume() {
        val s = update.value
        if (s is UpdateState.NeedsPermission && c.updater.canInstall()) viewModelScope.launch(Dispatchers.IO) { c.updater.install(s.info) }
    }

    fun installUpdate() {
        when (val s = update.value) {
            is UpdateState.Available -> viewModelScope.launch(Dispatchers.IO) { c.updater.install(s.info) }
            is UpdateState.NeedsPermission -> getApplication<Application>().startActivity(c.updater.permissionIntent())
            is UpdateState.ManualInstall -> c.updater.fallback(s.reason, force = true)
            is UpdateState.Installing, is UpdateState.Downloading -> Unit
            else -> viewModelScope.launch(Dispatchers.IO) { c.updater.checkAndInstall(autoInstall = true) }
        }
    }

    fun checkUpdate() = viewModelScope.launch(Dispatchers.IO) { c.updater.check() }

    fun refreshVintedUser(promptIfMissing: Boolean = false) = viewModelScope.launch {
        val host = settings.value.home.host
        val member = withContext(Dispatchers.Default) { runCatching { c.client.isMember(host) }.getOrDefault(false) }
        loggedIn.value = member
        if (!member && promptIfMissing) connectPrompt.value = true
        if (member) connectPrompt.value = false
        vintedUser.value = if (member) runCatching { c.client.currentUser(host) }.getOrNull() ?: "compte Vinted" else null
    }

    fun dismissConnectPrompt() { connectPrompt.value = false }

    fun runSelfTest() = viewModelScope.launch {
        selfTest.value = "Test en cours…"
        selfTest.value = c.client.selfTest(settings.value.home.host)
    }

    fun logoutVinted() { c.client.logout(); vintedUser.value = null; loggedIn.value = false }

    fun isMemberNow(): Boolean = runCatching { c.client.isMember(settings.value.home.host) }.getOrDefault(false)

    // ---------- profiles ----------

    fun selectProfile(id: String) = c.profiles.setActive(id)
    fun createProfile(name: String, emoji: String, hue: Float) = c.profiles.create(name, emoji, hue)
    fun editProfile(id: String, name: String, emoji: String, hue: Float) = c.profiles.update(id) { it.copy(name = name.trim().ifEmpty { it.name }, emoji = emoji, hue = hue) }
    fun deleteProfile(id: String) = c.profiles.delete(id)

    private fun updateActive(transform: (com.target.app.data.Profile) -> com.target.app.data.Profile) {
        val id = active.value?.id ?: return
        c.profiles.update(id, transform)
    }

    fun addKeyword(raw: String) {
        val k = raw.trim().replace(Regex("\\s+"), " ")
        if (k.isEmpty()) return
        // normalise the spelling of recognised brands/styles ("ralph laurent" -> "Ralph Lauren")
        val p = QueryParser.parse(k)
        val pretty = if (p.recognized.size == 1 && p.free.isEmpty()) p.recognized[0].label else k
        updateActive { pr -> if (pr.keywords.any { it.equals(pretty, true) }) pr else pr.copy(keywords = pr.keywords + pretty) }
    }

    fun removeKeyword(k: String) = updateActive { it.copy(keywords = it.keywords - k) }
    fun setMaxPrice(v: Double?) = updateActive { it.copy(maxPrice = v) }
    fun toggleSize(s: String) = updateActive { if (s in it.sizes) it.copy(sizes = it.sizes - s) else it.copy(sizes = it.sizes + s) }
    fun setProfileDeals(on: Boolean) = updateActive { it.copy(dealsEnabled = on) }

    fun addImages(uris: List<Uri>) {
        val profile = active.value ?: return
        viewModelScope.launch {
            for (uri in uris.take(8)) {
                val id = ProfileStore.newId()
                val file = File(c.profiles.refsDir, "$id.jpg")
                val ok = runCatching { c.analyzer.importImage(uri, file) }.isSuccess
                if (!ok) continue
                val ref = RefImage(id, file.absolutePath, System.currentTimeMillis())
                c.profiles.update(profile.id) { it.copy(images = it.images + ref) }
                analyzing.update { it + id }
                launch {
                    val a = runCatching { c.analyzer.analyze(file) }.getOrNull()
                    c.profiles.update(profile.id) { pr -> pr.copy(images = pr.images.map { if (it.id == id) it.copy(analysis = a) else it }) }
                    analyzing.update { it - id }
                }
            }
        }
    }

    fun removeImage(imageId: String) = updateActive { pr ->
        pr.images.firstOrNull { it.id == imageId }?.let { File(it.path).delete() }
        pr.copy(images = pr.images.filterNot { it.id == imageId })
    }

    fun toggleImageTag(imageId: String, tag: String) = updateActive { pr ->
        pr.copy(images = pr.images.map { img ->
            if (img.id != imageId) img else img.copy(hidden = if (tag in img.hidden) img.hidden - tag else img.hidden + tag)
        })
    }

    fun reanalyze(imageId: String) {
        val profile = active.value ?: return
        val img = profile.images.firstOrNull { it.id == imageId } ?: return
        analyzing.update { it + imageId }
        viewModelScope.launch {
            val a = runCatching { c.analyzer.analyze(File(img.path)) }.getOrNull()
            c.profiles.update(profile.id) { pr -> pr.copy(images = pr.images.map { if (it.id == imageId) it.copy(analysis = a, hidden = emptyList()) else it }) }
            analyzing.update { it - imageId }
        }
    }

    // ---------- search ----------

    fun searchProfile() {
        val p = active.value ?: return
        query.value = ""
        searchLabel.value = "${p.emoji} ${p.name}"
        collect(c.search.searchProfile(p))
    }

    fun searchText(text: String = query.value) {
        if (text.isBlank()) return searchProfile()
        searchLabel.value = "« ${text.trim()} »"
        collect(c.search.searchText(text.trim()))
    }

    private fun collect(flow: kotlinx.coroutines.flow.Flow<SearchState>) {
        searchJob?.cancel()
        tab.value = Tab.SEARCH
        searchJob = viewModelScope.launch {
            flow.collect { s ->
                s.results.forEach { cache[it.listing.id] = it }
                _search.value = s
            }
        }
    }

    /** Used by screenshot tests to render a result set without network. */
    internal fun showResults(state: SearchState) {
        state.results.forEach { cache[it.listing.id] = it }
        _search.value = state
    }

    fun cancelSearch() {
        searchJob?.cancel()
        _search.update { it?.copy(running = false) }
    }

    fun sorted(list: List<RankedListing>, mode: SortMode) = Ranker.sort(list, mode)

    // ---------- deals ----------

    fun scanDealsNow() = viewModelScope.launch(Dispatchers.Default) {
        for (p in profiles.value.filter { it.dealsEnabled && (it.keywords.isNotEmpty() || it.images.isNotEmpty()) }) {
            var last: SearchState? = null
            c.search.searchProfile(p, budget = 14).collect { last = it }
            val now = System.currentTimeMillis()
            val found = last?.results.orEmpty().filter { it.deal?.isDeal == true }
                .map { com.target.app.data.StoredDeal(it, p.id, p.name, now) }
            c.deals.add(found)
        }
    }

    fun removeDeal(id: Long) = c.deals.remove(id)
    fun clearDeals() = c.deals.clear()

    fun openDeal(id: Long) {
        c.deals.find(id)?.let { cache[id] = it.ranked }
        tab.value = Tab.DEALS
        nav.tryEmit(NavEvent.OpenItem(id))
    }

    fun ranked(id: Long): RankedListing? = cache[id] ?: c.deals.find(id)?.ranked?.also { cache[id] = it }

    fun loadDetails(id: Long) {
        if (_details.value.containsKey(id)) return
        val r = ranked(id) ?: return
        viewModelScope.launch {
            val d = runCatching { c.client.details(r.listing) }.getOrNull() ?: return@launch
            _details.update { it + (id to d) }
        }
    }

    // ---------- settings ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = settings.value
        c.settings.update(transform)
        val after = settings.value
        if (before.dealsEnabled != after.dealsEnabled || before.scanMinutes != after.scanMinutes)
            DealWorker.schedule(getApplication(), after.scanMinutes, after.dealsEnabled)
        if (before.home != after.home) refreshVintedUser()
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) { getApplication<Application>().cacheDir.deleteRecursively() }
}
