package com.anpurnama.f1_app.core.cache

import kotlinx.serialization.Serializable

@Serializable
data class CacheState(
    val schemaVersion: Int = CurrentSchemaVersion,
    val activeSeason: Int? = null,
    val snapshots: Map<String, ResourceSnapshot> = emptyMap(),
) {
    companion object {
        const val CurrentSchemaVersion: Int = 1
        val Default: CacheState = CacheState()
    }
}

@Serializable
data class ResourceSnapshot(
    val key: String,
    val season: Int?,
    val payloadKind: String,
    val payloadVersion: Int,
    val payloadJson: String,
    val fetchedAtEpochMs: Long,
    val staleAfterEpochMs: Long,
    val lastAttemptEpochMs: Long? = null,
    val lastAttemptStatus: RefreshAttemptStatus? = null,
)

@Serializable
sealed interface RefreshAttemptStatus {
    @Serializable
    data object Succeeded : RefreshAttemptStatus

    @Serializable
    data class Failed(val message: String) : RefreshAttemptStatus
}
