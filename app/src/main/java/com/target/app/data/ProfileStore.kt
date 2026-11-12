package com.target.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

@Serializable
private data class ProfilesFile(val profiles: List<Profile>, val activeId: String?)

/** Ref profiles ("Moi", "Ma sœur"...), persisted as JSON in private app storage. */
class ProfileStore(context: Context) {
    private val file = JsonFile(File(context.filesDir, "profiles.json"), ProfilesFile.serializer()) {
        val p = Profile(id = newId(), name = "Moi", emoji = "🎯", hue = 350f)
        ProfilesFile(listOf(p), p.id)
    }
    val refsDir: File = File(context.filesDir, "refs").apply { mkdirs() }

    private val initial = file.read()
    private val _profiles = MutableStateFlow(initial.profiles.ifEmpty { listOf(Profile(id = newId(), name = "Moi")) })
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()
    private val _activeId = MutableStateFlow(initial.activeId?.takeIf { id -> _profiles.value.any { it.id == id } } ?: _profiles.value.first().id)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    fun active(): Profile? = _profiles.value.firstOrNull { it.id == _activeId.value } ?: _profiles.value.firstOrNull()
    fun get(id: String): Profile? = _profiles.value.firstOrNull { it.id == id }

    @Synchronized
    private fun persist() = file.write(ProfilesFile(_profiles.value, _activeId.value))

    fun setActive(id: String) { _activeId.value = id; persist() }

    fun create(name: String, emoji: String, hue: Float): Profile {
        val p = Profile(id = newId(), name = name.trim().ifEmpty { "Profil" }, emoji = emoji, hue = hue)
        _profiles.update { it + p }
        _activeId.value = p.id
        persist()
        return p
    }

    fun update(id: String, transform: (Profile) -> Profile) {
        _profiles.update { list -> list.map { if (it.id == id) transform(it) else it } }
        persist()
    }

    fun delete(id: String) {
        val p = get(id) ?: return
        p.images.forEach { File(it.path).delete() }
        _profiles.update { list -> list.filterNot { it.id == id } }
        if (_profiles.value.isEmpty()) _profiles.value = listOf(Profile(id = newId(), name = "Moi"))
        if (_activeId.value == id) _activeId.value = _profiles.value.first().id
        persist()
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().take(12)
        val listSerializer = ListSerializer(Profile.serializer())
    }
}
