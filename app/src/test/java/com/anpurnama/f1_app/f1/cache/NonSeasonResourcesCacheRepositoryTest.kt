package com.anpurnama.f1_app.f1.cache

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.anpurnama.f1_app.core.cache.CacheResourceKey
import com.anpurnama.f1_app.core.cache.CacheState
import com.anpurnama.f1_app.core.cache.CacheStateSerializer
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.cache.SnapshotStore
import com.anpurnama.f1_app.f1.data.F1API_BASE
import com.anpurnama.f1_app.f1.data.WIKIPEDIA_REST_BASE
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

class NonSeasonResourcesCacheRepositoryTest {
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
        defaultRequest { url(F1API_BASE) }
    }

    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    // ── Circuit metadata ───────────────────────────────────────────────

    private fun circuitMetadataBody(circuitId: String = "bahrain") = """
    {
      "total": 1,
      "circuit": [{
        "circuitId": "$circuitId",
        "circuitName": "Bahrain International Circuit",
        "country": "Bahrain",
        "city": "Sakhir",
        "circuitLength": 5412,
        "lapRecord": "1:31.447",
        "firstParticipationYear": 2004,
        "numberOfCorners": 15,
        "fastestLapDriverId": "hamilton",
        "fastestLapTeamId": "mercedes",
        "fastestLapYear": 2019
      }]
    }
    """.trimIndent()

    @Test
    fun `circuit metadata refresh writes snapshot and returns success`() = runTest {
        val (store, scope) = newStore()
        val repo = NonSeasonResourcesCacheRepository(
            store = store,
            client = client { jsonOk(circuitMetadataBody()) },
            clock = FixedClock(1_000),
        )

        val result = repo.refreshCircuitMetadata("bahrain", RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        val cached = repo.observeCircuitMetadata("bahrain").first()!!
        assertEquals("Bahrain International Circuit", cached.data.name)
        assertEquals(5.412, cached.data.circuitLengthKm, 0.001)
        assertEquals(15, cached.data.numberOfCorners)
        assertEquals("1:31.447", cached.data.lapRecord?.time)
        // lapRecord is non-null in the test fixture — attribution fields are present
        scope.cancel()
    }

    @Test
    fun `circuit metadata failed refresh preserves existing content`() = runTest {
        val (store, scope) = newStore()
        val okRepo = NonSeasonResourcesCacheRepository(
            store = store,
            client = client { jsonOk(circuitMetadataBody()) },
            clock = FixedClock(1_000),
        )
        okRepo.refreshCircuitMetadata("bahrain", RefreshReason.StaleOpen)

        val failingRepo = NonSeasonResourcesCacheRepository(
            store = store,
            client = client { respondError(HttpStatusCode.ServiceUnavailable) },
            clock = FixedClock(2_000),
        )
        val result = failingRepo.refreshCircuitMetadata("bahrain", RefreshReason.PullToRefresh)

        assertEquals(RefreshResult.Failure("Server error (503)"), result)
        val cached = failingRepo.observeCircuitMetadata("bahrain").first()!!
        assertEquals("Bahrain International Circuit", cached.data.name)
        assertEquals(RefreshAttemptStatus.Failed("Server error (503)"), cached.snapshot.lastAttemptStatus)
        scope.cancel()
    }

    // ── Circuit most-wins ──────────────────────────────────────────────

    private fun circuitWinnersBody() = """
    {
      "MRData": {
        "total": "20",
        "RaceTable": {
          "circuitId": "bahrain",
          "Races": [
            {
              "season": "2025",
              "round": "1",
              "Results": [{
                "position": "1",
                "Driver": { "driverId": "max_verstappen", "givenName": "Max", "familyName": "Verstappen" },
                "Constructor": { "constructorId": "red_bull", "name": "Red Bull" }
              }]
            },
            {
              "season": "2024",
              "round": "1",
              "Results": [{
                "position": "1",
                "Driver": { "driverId": "max_verstappen", "givenName": "Max", "familyName": "Verstappen" },
                "Constructor": { "constructorId": "red_bull", "name": "Red Bull" }
              }]
            }
          ]
        }
      }
    }
    """.trimIndent()

    @Test
    fun `circuit most-wins refresh writes snapshot and caches aggregation`() = runTest {
        val (store, scope) = newStore()
        val repo = NonSeasonResourcesCacheRepository(
            store = store,
            client = client { jsonOk(circuitWinnersBody()) },
            clock = FixedClock(1_000),
        )

        val result = repo.refreshCircuitMostWins("bahrain", RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        val cached = repo.observeCircuitMostWins("bahrain").first()!!
        assertEquals("max_verstappen", cached.data.topDriver?.driverId)
        assertEquals(2, cached.data.topDriver?.wins)
        assertEquals("red_bull", cached.data.topTeam?.teamId)
        assertEquals(2, cached.data.totalRaces)
        scope.cancel()
    }

    @Test
    fun `circuit most-wins with no P1 rows caches valid nullable leaders`() = runTest {
        val (store, scope) = newStore()
        // Valid response with empty races array — no P1 rows exist.
        val emptyBody = """
        {
          "MRData": {
            "total": "0",
            "RaceTable": { "circuitId": "new_circuit", "Races": [] }
          }
        }
        """.trimIndent()
        val repo = NonSeasonResourcesCacheRepository(
            store = store,
            client = client { jsonOk(emptyBody) },
            clock = FixedClock(1_000),
        )

        val result = repo.refreshCircuitMostWins("new_circuit", RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        val cached = repo.observeCircuitMostWins("new_circuit").first()!!
        assertNull(cached.data.topDriver)
        assertNull(cached.data.topTeam)
        assertEquals(0, cached.data.totalRaces)
        scope.cancel()
    }

    // ── Wikipedia summaries ────────────────────────────────────────────

    private fun wikipediaBody(title: String = "Max Verstappen") = """
    {
      "title": "$title",
      "description": "Dutch racing driver",
      "extract": "Max Emilian Verstappen is a Dutch racing driver...",
      "content_urls": { "desktop": { "page": "https://en.wikipedia.org/wiki/$title" } },
      "thumbnail": { "source": "https://upload.wikimedia.org/...", "width": 200, "height": 300 }
    }
    """.trimIndent()

    @Test
    fun `wikipedia summary refresh writes snapshot and returns success`() = runTest {
        val (store, scope) = newStore()
        val repo = NonSeasonResourcesCacheRepository(
            store = store,
            client = client { mockWikipediaResponse() },
            clock = FixedClock(1_000),
        )

        val result = repo.refreshWikipediaSummary("Max_Verstappen", RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        val cached = repo.observeWikipediaSummary("Max_Verstappen").first()!!
        assertEquals("Max Verstappen", cached.data.title)
        assertEquals("Dutch racing driver", cached.data.description)
        assertEquals("Max Emilian Verstappen is a Dutch racing driver...", cached.data.extract)
        assertEquals("https://en.wikipedia.org/wiki/Max Verstappen", cached.data.contentUrl)
        scope.cancel()
    }

    @Test
    fun `single-flight deduplicates concurrent circuit metadata refreshes`() = runTest {
        val (store, scope) = newStore()
        var callCount = 0
        val repo = NonSeasonResourcesCacheRepository(
            store = store,
            client = client {
                callCount++
                kotlinx.coroutines.delay(100)
                jsonOk(circuitMetadataBody())
            },
            clock = FixedClock(1_000),
        )

        // Concurrent refreshes
        val results = mutableListOf<RefreshResult>()
        val job1 = launch {
            results.add(repo.refreshCircuitMetadata("bahrain", RefreshReason.StaleOpen))
        }
        val job2 = launch {
            results.add(repo.refreshCircuitMetadata("bahrain", RefreshReason.PullToRefresh))
        }
        joinAll(job1, job2)
        val result1 = results[0]
        val result2 = results[1]

        assertEquals(RefreshResult.Success, result1)
        assertEquals(RefreshResult.Success, result2)
        assertEquals(1, callCount)
        scope.cancel()
    }

    @Test
    fun `wikipedia summary failed refresh preserves existing content`() = runTest {
        val (store, scope) = newStore()
        val okRepo = NonSeasonResourcesCacheRepository(
            store = store,
            client = client { mockWikipediaResponse() },
            clock = FixedClock(1_000),
        )
        okRepo.refreshWikipediaSummary("Max_Verstappen", RefreshReason.StaleOpen)

        val failingRepo = NonSeasonResourcesCacheRepository(
            store = store,
            client = client { respondError(HttpStatusCode.ServiceUnavailable) },
            clock = FixedClock(2_000),
        )
        val result = failingRepo.refreshWikipediaSummary("Max_Verstappen", RefreshReason.PullToRefresh)

        assertEquals(RefreshResult.Failure("Server error (503)"), result)
        val cached = failingRepo.observeWikipediaSummary("Max_Verstappen").first()!!
        assertEquals("Max Verstappen", cached.data.title)
        assertEquals(RefreshAttemptStatus.Failed("Server error (503)"), cached.snapshot.lastAttemptStatus)
        scope.cancel()
    }

    // ── Season promotion survival ──────────────────────────────────────

    @Test
    fun `non-season resources survive active season promotion`() = runTest {
        val (store, scope) = newStore()
        // Pre-populate a non-season circuit metadata snapshot before any active season.
        val circuitKey = CacheResourceKeys.circuitMetadata("bahrain")
        store.writeSnapshot(ResourceSnapshot(
            key = circuitKey.value,
            season = null,
            payloadKind = circuitKey.payloadKind,
            payloadVersion = 1,
            payloadJson = circuitMetadataBody(),
            fetchedAtEpochMs = 1L,
            staleAfterEpochMs = Long.MAX_VALUE,
        ))
        val scheduleSnap = ResourceSnapshot(
            key = CacheResourceKeys.currentSeasonSchedule(2026).value,
            season = 2026,
            payloadKind = CacheResourceKeys.currentSeasonSchedule(2026).payloadKind,
            payloadVersion = 1,
            payloadJson = """{ "season": 2026, "races": [{"round":1,"raceName":"Bahrain GP","circuit":{"circuitId":"bahrain","circuitName":"Bahrain","circuitLength":"5412km"},"schedule":{"race":{"date":"2026-03-02","time":"15:00:00Z"}}}]}""",
            fetchedAtEpochMs = 1L,
            staleAfterEpochMs = Long.MAX_VALUE,
        )

        store.promoteActiveSeason(2026, scheduleSnap)

        // The circuit metadata should still exist
        val state = store.state.first()
        assertTrue(circuitKey.value in state.snapshots)
        assertEquals(2026, state.activeSeason)
        // The repository can still read it
        val repo = NonSeasonResourcesCacheRepository(store, client { jsonOk(circuitMetadataBody()) }, FixedClock(1_000))
        val cached = repo.observeCircuitMetadata("bahrain").first()
        assertNotNull(cached)
        assertEquals("Bahrain International Circuit", cached!!.data.name)
        scope.cancel()
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun MockRequestHandleScope.mockWikipediaResponse() = respond(
        content = wikipediaBody(),
        status = HttpStatusCode.OK,
        headers = headersOf(
            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
            // Wikipedia sends `max-age`; required by the user-agent pattern.
            HttpHeaders.UserAgent to listOf("F1app/1.0 (https://github.com/anpurnama/F1app)"),
        ),
    )

    private class FixedClock(private val epochMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(epochMs)
    }
}
