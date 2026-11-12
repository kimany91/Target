package com.target.engine

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/** Reads Vinted's session JWT (access_token_web) to tell an anonymous visitor from a logged-in member. */
object Session {
    fun payload(jwt: String?): JsonObject? {
        val part = jwt?.split('.')?.getOrNull(1) ?: return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(part.trimEnd('=').let { it + "=".repeat((4 - it.length % 4) % 4) })
            VintedJson.json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
        }.getOrNull()
    }

    /** True when the token belongs to a member account (not an anonymous browsing session). */
    fun isMember(jwt: String?): Boolean {
        val p = payload(jwt) ?: return false
        fun str(k: String) = (p[k] as? JsonPrimitive)?.content
        val scope = str("scope").orEmpty()
        if (scope.split(' ', ',').any { it == "user" || it == "member" }) return true
        if (scope == "public" || scope.contains("anon")) return false
        for (k in listOf("account_id", "user_id", "sub")) {
            val v = str(k)?.toLongOrNull()
            if (v != null && v > 0) return true
        }
        return false
    }

    fun cookie(cookies: String?, name: String): String? =
        cookies?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.takeIf { it.isNotEmpty() }
}
