package com.anpurnama.f1_app.core.cache

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.anpurnama.f1_app.f1.cache.CacheResourceKeys
import com.anpurnama.f1_app.f1.cache.SessionResultCacheKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotStoreTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private fun newStore(
        fileName: String = "cache-state.json",
        migrations: List<androidx.datastore.core.DataMigration<CacheState>> = emptyList(),
    ): Pair<SnapshotStore, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(tempFolder.newFolder(), fileName)
        val dataStore: DataStore<CacheState> = DataStoreFactory.create(
            serializer = CacheStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { CacheState.Default },
            migrations = migrations,
            scope = scope,
            produceFile = { file },
        )
        return SnapshotStore(dataStore) to scope
    }

    @Test
    fun resourceKeysCoverCurrentOfflineCacheResourceContracts() {
        val keys = listOf(
            CacheResourceKeys.currentSeasonSchedule(2026),
            CacheResourceKeys.nextRaceSession(2026),
            CacheResourceKeys.driverStandings(2026),
            CacheResourceKeys.constructorStandings(2026),
            CacheResourceKeys.driverCatalog(2026),
            CacheResourceKeys.constructorCatalog(2026),
            CacheResourceKeys.sessionResults(2026, 4, SessionResultCacheKind.Race),
            CacheResourceKeys.pitstops(2026, 4),
            CacheResourceKeys.circuitMetadata("monza"),
            CacheResourceKeys.circuitMostWins("monza"),
            CacheResourceKeys.wikipediaSummary("Scuderia Ferrari"),
        )

        assertEquals(keys.size, keys.map { it.value }.toSet().size)
        assertEquals("season:2026:schedule", keys[0].value)
        assertEquals("season:2026:round:4:session-results:race", keys[6].value)
        assertEquals("circuit:monza:metadata", keys[8].value)
        assertEquals("wikipedia:summary:scuderia-ferrari", keys[10].value)
    }

    @Test
    fun defaultStateIsCurrentSchemaWithNoActiveSeasonOrSnapshots() = runTest {
        val (store, scope) = newStore()

        val state = store.state.first()

        assertEquals(CacheState.CurrentSchemaVersion, state.schemaVersion)
        assertNull(state.activeSeason)
        assertTrue(state.snapshots.isEmpty())
        scope.cancel()
    }

    @Test
    fun writeSnapshotPersistsAndCanBeObservedByStableKey() = runTest {
        val (store, scope) = newStore()
        val key = CacheResourceKeys.currentSeasonSchedule(2026)
        val snapshot = ResourceSnapshot(
            key = key.value,
            season = 2026,
            payloadKind = "season.schedule",
            payloadVersion = 1,
            payloadJson = "{\"rounds\":24}",
            fetchedAtEpochMs = 100L,
            staleAfterEpochMs = 200L,
        )

        store.writeSnapshot(snapshot)

        assertEquals(snapshot, store.observeSnapshot(key).first())
        assertEquals(snapshot, store.state.first().snapshots[key.value])
        scope.cancel()
    }

    @Test
    fun observeSnapshotEmitsOnlyWhenThatKeyChanges() = runTest {
        val (store, scope) = newStore()
        val observedKey = CacheResourceKeys.driverStandings(2026)
        val otherKey = CacheResourceKeys.constructorStandings(2026)
        val emissions = mutableListOf<ResourceSnapshot?>()
        val job = backgroundScope.launch {
            store.observeSnapshot(observedKey).take(2).toList(emissions)
        }

        store.writeSnapshot(snapshotFor(otherKey, payloadJson = "{\"other\":true}"))
        store.writeSnapshot(snapshotFor(observedKey, payloadJson = "{\"drivers\":[]}"))
        job.join()

        assertEquals(listOf(null, snapshotFor(observedKey, payloadJson = "{\"drivers\":[]}")), emissions)
        scope.cancel()
    }

    @Test
    fun failedAttemptMetadataPreservesLastGoodPayload() = runTest {
        val (store, scope) = newStore()
        val key = CacheResourceKeys.nextRaceSession(2026)
        val snapshot = snapshotFor(key, payloadJson = "{\"race\":\"Bahrain\"}")
        store.writeSnapshot(snapshot)

        store.recordAttempt(
            key = key,
            attemptedAtEpochMs = 150L,
            status = RefreshAttemptStatus.Failed("offline"),
        )

        val updated = store.observeSnapshot(key).first()!!
        assertEquals(snapshot.payloadJson, updated.payloadJson)
        assertEquals(150L, updated.lastAttemptEpochMs)
        assertEquals(RefreshAttemptStatus.Failed("offline"), updated.lastAttemptStatus)
        scope.cancel()
    }

    @Test
    fun activeSeasonPromotionWritesScheduleAndPrunesOtherSeasonScopedSnapshotsOnly() = runTest {
        val (store, scope) = newStore()
        store.writeSnapshot(snapshotFor(CacheResourceKeys.driverStandings(2025), season = 2025))
        store.writeSnapshot(snapshotFor(CacheResourceKeys.sessionResults(2025, 3, SessionResultCacheKind.Race), season = 2025))
        val circuitKey = CacheResourceKeys.circuitMetadata("monza")
        store.writeSnapshot(snapshotFor(circuitKey, season = null))
        val schedule = snapshotFor(CacheResourceKeys.currentSeasonSchedule(2026), season = 2026)

        store.promoteActiveSeason(season = 2026, scheduleSnapshot = schedule)

        val state = store.state.first()
        assertEquals(2026, state.activeSeason)
        assertEquals(schedule, state.snapshots[schedule.key])
        assertFalse(state.snapshots.containsKey(CacheResourceKeys.driverStandings(2025).value))
        assertFalse(state.snapshots.containsKey(CacheResourceKeys.sessionResults(2025, 3, SessionResultCacheKind.Race).value))
        assertTrue(state.snapshots.containsKey(circuitKey.value))
        scope.cancel()
    }

    @Test
    fun activeSeasonPromotionRejectsNonScheduleSnapshots() = runTest {
        val (store, scope) = newStore()
        val standings = snapshotFor(CacheResourceKeys.driverStandings(2026), season = 2026)

        val error = runCatching {
            store.promoteActiveSeason(season = 2026, scheduleSnapshot = standings)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertNull(store.state.first().activeSeason)
        scope.cancel()
    }

    @Test
    fun olderSchemaFileReadsAsCurrentSchemaAndPreservesSnapshots() = runTest {
        val file = File(tempFolder.newFolder(), "cache-state.json")
        file.writeText(
            """
            {
              "schemaVersion": 0,
              "activeSeason": 2026,
              "snapshots": {
                "season:2026:schedule": {
                  "key": "season:2026:schedule",
                  "season": 2026,
                  "payloadKind": "season.schedule",
                  "payloadVersion": 1,
                  "payloadJson": "{}",
                  "fetchedAtEpochMs": 100,
                  "staleAfterEpochMs": 200
                }
              }
            }
            """.trimIndent(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = DataStoreFactory.create(
            serializer = CacheStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { CacheState.Default },
            migrations = listOf(CacheStateSchemaMigration),
            scope = scope,
            produceFile = { file },
        )
        val store = SnapshotStore(dataStore)

        val migrated = store.state.first()

        assertEquals(CacheState.CurrentSchemaVersion, migrated.schemaVersion)
        assertEquals(2026, migrated.activeSeason)
        assertTrue(migrated.snapshots.containsKey("season:2026:schedule"))
        scope.cancel()
    }

    @Test
    fun futureSchemaFileRecoversAsNoUsableCache() = runTest {
        val file = File(tempFolder.newFolder(), "cache-state.json")
        file.writeText(
            """
            {
              "schemaVersion": 999,
              "activeSeason": 2026,
              "snapshots": {}
            }
            """.trimIndent(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = DataStoreFactory.create(
            serializer = CacheStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { CacheState.Default },
            migrations = listOf(CacheStateSchemaMigration),
            scope = scope,
            produceFile = { file },
        )
        val store = SnapshotStore(dataStore)

        assertEquals(CacheState.Default, store.state.first())
        scope.cancel()
    }

    @Test
    fun corruptStoreRecoversToDefaultState() = runTest {
        val file = File(tempFolder.newFolder(), "cache-state.json")
        file.writeText("not-json")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = DataStoreFactory.create(
            serializer = CacheStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { CacheState.Default },
            scope = scope,
            produceFile = { file },
        )
        val store = SnapshotStore(dataStore)

        val state = store.state.first()

        assertEquals(CacheState.Default, state)
        scope.cancel()
    }

    private fun snapshotFor(
        key: CacheResourceKey,
        season: Int? = key.season,
        payloadJson: String = "{}",
    ) = ResourceSnapshot(
        key = key.value,
        season = season,
        payloadKind = key.payloadKind,
        payloadVersion = 1,
        payloadJson = payloadJson,
        fetchedAtEpochMs = 100L,
        staleAfterEpochMs = 200L,
    )
}
