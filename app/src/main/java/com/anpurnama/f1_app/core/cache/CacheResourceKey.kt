package com.anpurnama.f1_app.core.cache

/** Stable persisted resource key plus metadata needed by the snapshot store. */
data class CacheResourceKey(
    val value: String,
    val payloadKind: String,
    val season: Int?,
)
