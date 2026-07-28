package com.anpurnama.f1_app.core.cache

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SnapshotStore(private val dataStore: DataStore<CacheState>) {
    val state: Flow<CacheState> = dataStore.data.distinctUntilChanged()

    fun observeSnapshot(key: CacheResourceKey): Flow<ResourceSnapshot?> = state
        .map { it.snapshots[key.value] }
        .distinctUntilChanged()

    suspend fun writeSnapshot(snapshot: ResourceSnapshot) {
        dataStore.updateData { current ->
            current.copy(
                schemaVersion = CacheState.CurrentSchemaVersion,
                snapshots = current.snapshots + (snapshot.key to snapshot),
            )
        }
    }

    suspend fun recordAttempt(
        key: CacheResourceKey,
        attemptedAtEpochMs: Long,
        status: RefreshAttemptStatus,
    ) {
        dataStore.updateData { current ->
            val snapshot = current.snapshots[key.value] ?: return@updateData current
            current.copy(
                schemaVersion = CacheState.CurrentSchemaVersion,
                snapshots = current.snapshots + (key.value to snapshot.copy(
                    lastAttemptEpochMs = attemptedAtEpochMs,
                    lastAttemptStatus = status,
                )),
            )
        }
    }

    suspend fun promoteActiveSeason(season: Int, scheduleSnapshot: ResourceSnapshot) {
        require(scheduleSnapshot.season == season) { "Schedule snapshot season must match promoted season." }
        require(scheduleSnapshot.key == "season:$season:schedule") { "Active season promotion requires the current-season schedule snapshot." }
        require(scheduleSnapshot.payloadKind == "season.schedule") { "Active season promotion requires a schedule payload." }
        dataStore.updateData { current ->
            val retained = current.snapshots.filterValues { it.season == null || it.season == season }
            current.copy(
                schemaVersion = CacheState.CurrentSchemaVersion,
                activeSeason = season,
                snapshots = retained + (scheduleSnapshot.key to scheduleSnapshot),
            )
        }
    }
}

object CacheStateSchemaMigration : DataMigration<CacheState> {
    override suspend fun shouldMigrate(currentData: CacheState): Boolean =
        currentData.schemaVersion != CacheState.CurrentSchemaVersion

    override suspend fun migrate(currentData: CacheState): CacheState = when {
        currentData.schemaVersion < CacheState.CurrentSchemaVersion ->
            currentData.copy(schemaVersion = CacheState.CurrentSchemaVersion)
        else -> CacheState.Default
    }

    override suspend fun cleanUp() = Unit
}
