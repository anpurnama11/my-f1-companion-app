package com.anpurnama.f1_app.f1.cache

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.anpurnama.f1_app.core.cache.CacheResourceKey as CoreCacheResourceKey
import com.anpurnama.f1_app.core.cache.CacheState
import com.anpurnama.f1_app.core.cache.CacheStateSerializer
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.cache.SnapshotStore
import com.anpurnama.f1_app.f1.data.JOLPICA_BASE
import com.anpurnama.f1_app.f1.model.SessionType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionResultsCacheRepositoryTest {
    @get:Rule val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun newStore(): Pair<SnapshotStore, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(tempFolder.newFolder(), "cache-state.json")
        return SnapshotStore(
            DataStoreFactory.create(
                serializer = CacheStateSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { CacheState.Default },
                scope = scope,
                produceFile = { file },
            )
        ) to scope
    }

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        defaultRequest { url(JOLPICA_BASE) }
    }

    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    /** Minimal Jolpica race results body with one result. */
    private fun raceResultsBody(season: Int = 2026, round: Int = 1) = """
    {
      "MRData": {
        "RaceTable": {
          "season": "$season",
          "round": "$round",
          "Races": [{
            "season": "$season",
            "round": "$round",
            "raceName": "Bahrain GP",
            "Circuit": { "circuitId": "bahrain", "circuitName": "Bahrain International Circuit", "Location": { "locality": "Sakhir", "country": "Bahrain" } },
            "Results": [{
              "number": "1",
              "position": "1",
              "positionText": "1",
              "points": "25",
              "grid": "1",
              "status": "Finished",
              "Driver": { "driverId": "max_verstappen", "permanentNumber": "1", "code": "VER", "givenName": "Max", "familyName": "Verstappen" },
              "Constructor": { "constructorId": "red_bull", "name": "Red Bull" },
              "Time": { "millis": "5042252", "time": "1:24:04.252" },
              "FastestLap": { "rank": "1", "lap": "10", "Time": { "time": "1:32.000" } }
            }]
          }]
        }
      }
    }
    """.trimIndent()

    private fun qualifyingBody(season: Int = 2026, round: Int = 1) = """
    {
      "MRData": {
        "RaceTable": {
          "season": "$season",
          "round": "$round",
          "Races": [{
            "season": "$season",
            "round": "$round",
            "raceName": "Bahrain GP",
            "Circuit": { "circuitId": "bahrain", "circuitName": "Bahrain International Circuit", "Location": { "locality": "Sakhir", "country": "Bahrain" } },
            "QualifyingResults": [{
              "number": "1",
              "position": "1",
              "Driver": { "driverId": "max_verstappen", "permanentNumber": "1", "code": "VER", "givenName": "Max", "familyName": "Verstappen" },
              "Constructor": { "constructorId": "red_bull", "name": "Red Bull" },
              "Q1": "1:30.000",
              "Q2": "1:29.500",
              "Q3": "1:29.000"
            }]
          }]
        }
      }
    }
    """.trimIndent()

    private fun pitstopsBody(season: Int = 2026, round: Int = 1) = """
    {
      "MRData": {
        "RaceTable": {
          "Races": [{
            "PitStops": [{
              "driverId": "max_verstappen",
              "duration": "21.5"
            }]
          }]
        }
      }
    }
    """.trimIndent()

    private fun emptyPitstopsBody() = """
    {
      "MRData": {
        "RaceTable": { "Races": [] }
      }
    }
    """.trimIndent()

    private fun alphaPracticeBody(season: Int = 2026, round: Int = 1) = """
    {
      "data": {
        "season": { "year": $season },
        "round": { "number": $round, "name": "Bahrain GP" },
        "results": [{
          "driver": { "id": "driver_019", "abbreviation": "ANT", "given_name": "Andrea Kimi", "family_name": "Antonelli" },
          "team": { "id": "team_131", "name": "Mercedes" },
          "position": 1,
          "time": "1:31.000",
          "car_number": 12,
          "components": {}
        }]
      }
    }
    """.trimIndent()

    private fun driverCatalogBody(season: Int = 2026) = """
    {
      "season": $season,
      "drivers": [{
        "driverId": "antonelli",
        "name": "Andrea Kimi",
        "surname": "Antonelli",
        "number": 12,
        "shortName": "ANT",
        "teamId": "mercedes"
      }]
    }
    """.trimIndent()

    /**
     * Season schedule with a known session slot (race at 2026-03-02T15:00:00Z).
     * Used for plausibly-complete gate tests. The clock can be set before/after
     * this time + buffer to gate the fetch.
     */
    private fun scheduleWithSlugGate(
        season: Int = 2026,
        sessionDate: String = "2026-03-02",
        sessionTime: String = "15:00:00Z",
    ) = """
    {
      "season": $season,
      "races": [{
        "round": 1,
        "raceName": "Bahrain GP",
        "circuit": { "circuitId": "bahrain", "circuitName": "Bahrain", "circuitLength": "5412km" },
        "schedule": {
          "race": { "date": "$sessionDate", "time": "$sessionTime" },
          "qualy": { "date": "$sessionDate", "time": "12:00:00Z" },
          "fp1": { "date": "$sessionDate", "time": "08:00:00Z" },
          "fp2": { "date": "$sessionDate", "time": "09:30:00Z" },
          "fp3": { "date": "$sessionDate", "time": "10:30:00Z" },
          "sprintQualy": { "date": "$sessionDate", "time": "10:00:00Z" },
          "sprintRace": { "date": "$sessionDate", "time": "11:00:00Z" }
        }
      }]
    }
    """.trimIndent()

    // ── Race result tests ──────────────────────────────────────────────

    @Test
    fun `race result refresh writes snapshot and returns success`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026, raceBody = true))
        val repo = SessionResultsCacheRepository(
            store = store,
            client = client { jsonOk(raceResultsBody()) },
            clock = FixedClock(raceCompleteEpochMs),
        )

        val result = repo.refreshSessionResult(2026, 1, SessionType.Race, RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        val cached = repo.observeSessionResult(2026, 1, SessionType.Race).first()!!
        assertEquals("Max Verstappen", cached.data.raceResults.single().driverName)
        assertEquals("1:32.000", cached.data.fastestLap?.time)
        assertEquals(SessionType.Race, cached.data.session)
        scope.cancel()
    }

    @Test
    fun `qualifying result refresh writes snapshot and returns success`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026, raceBody = true))
        val repo = SessionResultsCacheRepository(
            store = store,
            client = client { jsonOk(qualifyingBody()) },
            clock = FixedClock(raceCompleteEpochMs),
        )

        val result = repo.refreshSessionResult(2026, 1, SessionType.Quali, RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        val cached = repo.observeSessionResult(2026, 1, SessionType.Quali).first()!!
        assertEquals("max_verstappen", cached.data.qualifyingResults.single().driverId)
        assertEquals("1:29.000", cached.data.qualifyingResults.single().q3)
        scope.cancel()
    }

    @Test
    fun `failed session result refresh preserves existing content and records attempt`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026, raceBody = true))
        val okRepo = SessionResultsCacheRepository(
            store = store,
            client = client { jsonOk(raceResultsBody()) },
            clock = FixedClock(raceCompleteEpochMs),
        )
        okRepo.refreshSessionResult(2026, 1, SessionType.Race, RefreshReason.StaleOpen)

        val failingRepo = SessionResultsCacheRepository(
            store = store,
            client = client { respondError(HttpStatusCode.ServiceUnavailable) },
            clock = FixedClock(raceCompleteEpochMs + 1000),
        )
        val result = failingRepo.refreshSessionResult(2026, 1, SessionType.Race, RefreshReason.PullToRefresh)

        assertEquals(RefreshResult.Failure("Server error (503)"), result)
        // Cached data remains visible
        val cached = failingRepo.observeSessionResult(2026, 1, SessionType.Race).first()!!
        assertEquals("Max Verstappen", cached.data.raceResults.single().driverName)
        // Attempt metadata updated
        assertEquals(RefreshAttemptStatus.Failed("Server error (503)"), cached.snapshot.lastAttemptStatus)
        assertTrue((cached.snapshot.lastAttemptEpochMs ?: 0) > raceCompleteEpochMs)
        scope.cancel()
    }

    @Test
    fun `observing non-cached session result returns null`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026))
        val repo = SessionResultsCacheRepository(store, client { jsonOk(raceResultsBody()) }, FixedClock(raceCompleteEpochMs))

        val cached = repo.observeSessionResult(2026, 1, SessionType.Race).first()

        assertNull(cached)
        scope.cancel()
    }

    @Test
    fun `alpha session cache observation translates driver and team ids from cached driver catalog`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026))
        store.writeSnapshot(snapshotFor(
            key = CacheResourceKeys.driverCatalog(2026),
            payloadJson = driverCatalogBody(),
        ))
        store.writeSnapshot(snapshotFor(
            key = CacheResourceKeys.sessionResults(2026, 1, SessionType.FP1),
            payloadJson = alphaPracticeBody(),
        ))
        val repo = SessionResultsCacheRepository(store, client { respondError(HttpStatusCode.NotFound) }, FixedClock(1_000))

        val cached = repo.observeSessionResult(2026, 1, SessionType.FP1).first()!!

        val row = cached.data.practiceResults.single()
        assertEquals("antonelli", row.driverId)
        assertEquals("mercedes", row.teamId)
        assertEquals("Andrea Kimi Antonelli", row.driverName)
        scope.cancel()
    }

    @Test
    fun `pitstop refresh caches fastest pitstop from list`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026))
        val repo = SessionResultsCacheRepository(
            store = store,
            client = client { jsonOk(pitstopsBody()) },
            clock = FixedClock(1_000),
        )

        val result = repo.refreshPitstops(2026, 1, RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        val cached = repo.observePitstops(2026, 1).first()!!
        assertNotNull(cached.data)
        assertEquals("max_verstappen", cached.data?.driverId)
        assertEquals(21.5, cached.data!!.durationSeconds, 0.001)
        scope.cancel()
    }

    @Test
    fun `empty pitstops cache as null FastestPitstop`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026))
        val repo = SessionResultsCacheRepository(
            store = store,
            client = client { jsonOk(emptyPitstopsBody()) },
            clock = FixedClock(1_000),
        )

        val result = repo.refreshPitstops(2026, 1, RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        val cached = repo.observePitstops(2026, 1).first()!!
        assertNull(cached.data)
        scope.cancel()
    }

    @Test
    fun `future session gate skips network and reports not yet complete when no cache exists`() = runTest {
        val (store, scope) = newStore()
        // Schedule says race at 15:00Z, buffer for race is 4h → complete after 19:00Z.
        val futureClock = FixedClock( /* 14:00Z — race hasn't started yet */ beforeRaceEpochMs)
        store.promoteActiveSeason(2026, gateScheduleSnapshot(2026))
        val repo = SessionResultsCacheRepository(
            store = store,
            client = client { jsonOk(raceResultsBody()) },
            clock = futureClock,
        )

        val result = repo.refreshSessionResult(2026, 1, SessionType.Race, RefreshReason.StaleOpen)

        // Should skip network (no snapshot written) and tell callers not to bypass the gate.
        assertEquals(RefreshResult.Failure("Session not yet complete"), result)
        assertNull(repo.observeSessionResult(2026, 1, SessionType.Race).first())
        scope.cancel()
    }

    @Test
    fun `future session gate does not overwrite existing cached content`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, gateScheduleSnapshot(2026))
        val pastClock = FixedClock(raceCompleteEpochMs) // well after race is complete
        val repo = SessionResultsCacheRepository(
            store = store,
            client = client { jsonOk(raceResultsBody()) },
            clock = pastClock,
        )
        repo.refreshSessionResult(2026, 1, SessionType.Race, RefreshReason.StaleOpen)
        assertNotNull(repo.observeSessionResult(2026, 1, SessionType.Race).first())

        // Now move clock to future (before session + buffer would be)
        // but we already have cached data.
        val futureClock = FixedClock(beforeRaceEpochMs) // 14:00Z, before race start 15:00Z
        val repo2 = SessionResultsCacheRepository(
            store = store,
            client = client { respondError(HttpStatusCode.ServiceUnavailable) },
            clock = futureClock,
        )
        val result = repo2.refreshSessionResult(2026, 1, SessionType.Race, RefreshReason.PullToRefresh)

        // Gate should skip the network (no 503), preserve cached content
        assertEquals(RefreshResult.Success, result)
        val cached = repo2.observeSessionResult(2026, 1, SessionType.Race).first()!!
        assertEquals("Max Verstappen", cached.data.raceResults.single().driverName)
        scope.cancel()
    }

    @Test
    fun `single-flight deduplicates concurrent refresh requests`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, gateScheduleSnapshot(2026))
        var callCount = 0
        val repo = SessionResultsCacheRepository(
            store = store,
            client = client {
                callCount++
                // Return after a small delay to allow overlapping requests
                kotlinx.coroutines.delay(100)
                jsonOk(raceResultsBody())
            },
            clock = FixedClock(raceCompleteEpochMs),
        )

        // Concurrent refreshes
        val results = mutableListOf<RefreshResult>()
        val job1 = launch {
            results.add(repo.refreshSessionResult(2026, 1, SessionType.Race, RefreshReason.StaleOpen))
        }
        val job2 = launch {
            results.add(repo.refreshSessionResult(2026, 1, SessionType.Race, RefreshReason.PullToRefresh))
        }
        joinAll(job1, job2)
        val result1 = results[0]
        val result2 = results[1]

        assertEquals(RefreshResult.Success, result1)
        assertEquals(RefreshResult.Success, result2)
        // Only one network request should have been made
        assertEquals(1, callCount)
        scope.cancel()
    }

    @Test
    fun `completed session passes plausibly-complete gate`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, gateScheduleSnapshot(2026))
        val completeClock = FixedClock(raceCompleteEpochMs) // well after start + 4h buffer
        val repo = SessionResultsCacheRepository(
            store = store,
            client = client { jsonOk(raceResultsBody()) },
            clock = completeClock,
        )

        val result = repo.refreshSessionResult(2026, 1, SessionType.Race, RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        assertNotNull(repo.observeSessionResult(2026, 1, SessionType.Race).first())
        scope.cancel()
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /** Schedule snapshot with all session types defined for gate tests. */
    private fun gateScheduleSnapshot(season: Int) = ResourceSnapshot(
        key = CacheResourceKeys.currentSeasonSchedule(season).value,
        season = season,
        payloadKind = CacheResourceKeys.currentSeasonSchedule(season).payloadKind,
        payloadVersion = 1,
        payloadJson = scheduleWithSlugGate(season),
        fetchedAtEpochMs = 1L,
        staleAfterEpochMs = 2L,
    )

    /**
     * Schedule snapshot for the active season. When [raceBody] is true, the
     * body also includes Jolpica response fields used by schedule deserialization.
     */
    private fun scheduleSnapshot(season: Int, raceBody: Boolean = false) = ResourceSnapshot(
        key = CacheResourceKeys.currentSeasonSchedule(season).value,
        season = season,
        payloadKind = CacheResourceKeys.currentSeasonSchedule(season).payloadKind,
        payloadVersion = 1,
        payloadJson = if (raceBody) scheduleWithSlugGate(season) else """
            { "season": $season,
              "races": [{
                "round": 1,
                "raceName": "Bahrain GP",
                "circuit": { "circuitId": "bahrain", "circuitName": "Bahrain", "circuitLength": "5412km" },
                "schedule": { "race": { "date": "$season-03-02", "time": "15:00:00Z" } }
              }]
            }
        """.trimIndent(),
        fetchedAtEpochMs = 1L,
        staleAfterEpochMs = 2L,
    )

    private fun snapshotFor(
        key: CoreCacheResourceKey,
        payloadJson: String,
    ) = ResourceSnapshot(
        key = key.value,
        season = key.season,
        payloadKind = key.payloadKind,
        payloadVersion = 1,
        payloadJson = payloadJson,
        fetchedAtEpochMs = 1L,
        staleAfterEpochMs = Long.MAX_VALUE,
        lastAttemptEpochMs = 1L,
        lastAttemptStatus = RefreshAttemptStatus.Succeeded,
    )

    companion object {
        /** 2026-03-02T15:00:00Z in epoch milliseconds */
        private val raceStartEpochMs = Instant.parse("2026-03-02T15:00:00Z").toEpochMilliseconds()
        /** After 4h buffer: 2026-03-02T19:00:00Z */
        private val raceCompleteEpochMs = Instant.parse("2026-03-02T19:00:00Z").toEpochMilliseconds()
        /** Before race: 2026-03-02T14:00:00Z */
        private val beforeRaceEpochMs = Instant.parse("2026-03-02T14:00:00Z").toEpochMilliseconds()
    }

    private class FixedClock(private val epochMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(epochMs)
    }
}
