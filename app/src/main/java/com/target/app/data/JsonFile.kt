package com.target.app.data

import android.util.AtomicFile
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Crash-safe JSON persistence (AtomicFile: write to a temp file then rename). */
class JsonFile<T>(file: File, private val serializer: KSerializer<T>, private val default: () -> T) {
    private val atomic = AtomicFile(file)

    fun read(): T = runCatching {
        if (!atomic.baseFile.exists()) default() else JSON.decodeFromString(serializer, atomic.readFully().decodeToString())
    }.getOrElse { default() }

    @Synchronized
    fun write(value: T) {
        val out = atomic.startWrite()
        try {
            out.write(JSON.encodeToString(serializer, value).encodeToByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
        }
    }

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    }
}
