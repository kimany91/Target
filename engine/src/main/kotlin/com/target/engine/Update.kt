package com.target.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Semantic version "1.4.12" (a leading "v" is accepted). */
data class Version(val parts: List<Int>) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val a = parts.getOrElse(i) { 0 }
            val b = other.parts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    override fun toString() = parts.joinToString(".")

    companion object {
        fun parse(s: String?): Version? {
            if (s.isNullOrBlank()) return null
            val core = s.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            val parts = core.split('.').map { it.toIntOrNull() ?: return null }
            return if (parts.isEmpty()) null else Version(parts)
        }
    }
}

data class ReleaseInfo(
    val version: Version,
    val tag: String,
    val apkUrl: String,
    val apkSize: Long?,
    val sha256Url: String?,
    val notes: String,
)

object Releases {
    /** Parses GitHub's /releases/latest response. Returns null if no APK asset is attached. */
    fun parseLatest(body: String): ReleaseInfo? {
        val o = runCatching { VintedJson.json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        if ((o["draft"] as? JsonPrimitive)?.content == "true" || (o["prerelease"] as? JsonPrimitive)?.content == "true") return null
        val tag = (o["tag_name"] as? JsonPrimitive)?.content ?: return null
        val version = Version.parse(tag) ?: return null
        val assets = (o["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        fun name(a: JsonObject) = (a["name"] as? JsonPrimitive)?.content.orEmpty()
        fun url(a: JsonObject) = (a["browser_download_url"] as? JsonPrimitive)?.content
        val apk = assets.firstOrNull { name(it).endsWith(".apk", ignoreCase = true) } ?: return null
        val apkUrl = url(apk) ?: return null
        if (!apkUrl.startsWith("https://github.com/") && !apkUrl.startsWith("https://objects.githubusercontent.com/")) return null
        val sha = assets.firstOrNull { name(it).equals(name(apk) + ".sha256", true) }?.let(::url)
        return ReleaseInfo(
            version, tag, apkUrl, (apk["size"] as? JsonPrimitive)?.longOrNull, sha,
            (o["body"] as? JsonPrimitive)?.content.orEmpty(),
        )
    }

    fun isNewer(current: String, release: ReleaseInfo): Boolean {
        val cur = Version.parse(current) ?: return true
        return release.version > cur
    }
}
