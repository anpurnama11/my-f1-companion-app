package com.anpurnama.f1_app.core.cache

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object CacheStateSerializer : Serializer<CacheState> {
    override val defaultValue: CacheState = CacheState.Default

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun readFrom(input: InputStream): CacheState = try {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) CacheState.Default else json.decodeFromString<CacheState>(text).normalized()
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot decode CacheState", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Cannot decode CacheState", e)
    }

    override suspend fun writeTo(t: CacheState, output: OutputStream) {
        output.write(json.encodeToString(t.normalized()).encodeToByteArray())
    }
}

private fun CacheState.normalized(): CacheState = when {
    schemaVersion == CacheState.CurrentSchemaVersion -> this
    schemaVersion < CacheState.CurrentSchemaVersion -> copy(schemaVersion = CacheState.CurrentSchemaVersion)
    else -> CacheState.Default
}
