package com.anpurnama.f1_app.f1.cache

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.anpurnama.f1_app.core.cache.CacheState
import com.anpurnama.f1_app.core.cache.CacheStateSerializer
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.cache.SnapshotStore
import com.anpurnama.f1_app.f1.data.F1API_BASE
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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CurrentSeasonResourcesCacheRepositoryTest {
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

    @Test
    fun `standings refresh writes active-season snapshots and empty standings are valid`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026))
        var requestedUrl = ""
        val repo = CurrentSeasonResourcesCacheRepository(
            store = store,
            client = client { request ->
                requestedUrl = request.url.toString()
                jsonOk(emptyDriverStandingsBody())
            },
            clock = FixedClock(1_000),
        )

        val result = repo.refreshDriverStandings(RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        val cached = repo.observeDriverStandings().first()!!
        assertEquals(emptyList<Any>(), cached.data)
        assertEquals(CacheResourceKeys.driverStandings(2026).value, cached.snapshot.key)
        assertEquals(RefreshAttemptStatus.Succeeded, cached.snapshot.lastAttemptStatus)
        assertTrue(requestedUrl.endsWith("/2026/driverStandings.json"))
        scope.cancel()
    }

    @Test
    fun `failed standings refresh preserves last good payload and records metadata`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026))
        val repo = CurrentSeasonResourcesCacheRepository(store, client { jsonOk(driverStandingsBody()) }, FixedClock(1_000))
        repo.refreshDriverStandings(RefreshReason.StaleOpen)

        val failing = CurrentSeasonResourcesCacheRepository(
            store = store,
            client = client { respondError(HttpStatusCode.ServiceUnavailable) },
            clock = FixedClock(2_000),
        )
        val result = failing.refreshDriverStandings(RefreshReason.PullToRefresh)

        assertEquals(RefreshResult.Failure("Server error (503)"), result)
        val cached = failing.observeDriverStandings().first()!!
        assertEquals("Max Verstappen", cached.data.single().driverName)
        assertEquals(2_000L, cached.snapshot.lastAttemptEpochMs)
        assertEquals(RefreshAttemptStatus.Failed("Server error (503)"), cached.snapshot.lastAttemptStatus)
        scope.cancel()
    }

    @Test
    fun `off season next-race empty response caches as valid null payload`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026))
        val repo = CurrentSeasonResourcesCacheRepository(
            store = store,
            client = client { jsonOk("""{ "season": 2026, "round": 0, "race": [] }""") },
            clock = FixedClock(1_000),
        )

        val result = repo.refreshNextRace(RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Success, result)
        val cached = repo.observeNextRace().first()!!
        assertEquals(null, cached.data)
        assertEquals(CacheResourceKeys.nextRaceSession(2026).value, cached.snapshot.key)
        scope.cancel()
    }

    @Test
    fun `next-race refresh rejects current endpoint response from a different season`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026))
        val repo = CurrentSeasonResourcesCacheRepository(
            store = store,
            client = client { jsonOk("""{ "season": 2027, "round": 1, "race": [] }""") },
            clock = FixedClock(1_000),
        )

        val result = repo.refreshNextRace(RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Failure("Invalid season.next-race-session payload"), result)
        assertEquals(null, repo.observeNextRace().first())
        assertTrue(CacheResourceKeys.nextRaceSession(2026).value !in store.state.first().snapshots)
        scope.cancel()
    }

    @Test
    fun `catalog refreshes write active-season driver and team snapshots`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026))
        var request = 0
        val requestedUrls = mutableListOf<String>()
        val repo = CurrentSeasonResourcesCacheRepository(
            store = store,
            client = client { req ->
                requestedUrls += req.url.toString()
                request++
                if (request == 1) jsonOk("""{ "season": 2026, "drivers": [] }""")
                else jsonOk("""{ "season": 2026, "teams": [] }""")
            },
            clock = FixedClock(1_000),
        )

        assertEquals(RefreshResult.Success, repo.refreshDriverCatalog(RefreshReason.StaleOpen))
        assertEquals(RefreshResult.Success, repo.refreshTeamCatalog(RefreshReason.StaleOpen))

        assertTrue(CacheResourceKeys.driverCatalog(2026).value in store.state.first().snapshots)
        assertTrue(CacheResourceKeys.constructorCatalog(2026).value in store.state.first().snapshots)
        assertEquals("catalog.drivers", store.state.first().snapshots.getValue(CacheResourceKeys.driverCatalog(2026).value).payloadKind)
        assertEquals("catalog.constructors", store.state.first().snapshots.getValue(CacheResourceKeys.constructorCatalog(2026).value).payloadKind)
        assertTrue(requestedUrls[0].endsWith("/2026/drivers"))
        assertTrue(requestedUrls[1].endsWith("/2026/teams"))
        scope.cancel()
    }

    @Test
    fun `constructor standings refresh uses active-season endpoint`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, scheduleSnapshot(2026))
        var requestedUrl = ""
        val repo = CurrentSeasonResourcesCacheRepository(
            store = store,
            client = client { request ->
                requestedUrl = request.url.toString()
                jsonOk("""{ "MRData": { "StandingsTable": { "StandingsLists": [] } } }""")
            },
            clock = FixedClock(1_000),
        )

        assertEquals(RefreshResult.Success, repo.refreshConstructorStandings(RefreshReason.StaleOpen))

        assertTrue(requestedUrl.endsWith("/2026/constructorStandings.json"))
        scope.cancel()
    }

    private fun scheduleSnapshot(season: Int) = ResourceSnapshot(
        key = CacheResourceKeys.currentSeasonSchedule(season).value,
        season = season,
        payloadKind = CacheResourceKeys.currentSeasonSchedule(season).payloadKind,
        payloadVersion = 1,
        payloadJson = """
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

    private fun emptyDriverStandingsBody() = """
        { "MRData": { "StandingsTable": { "StandingsLists": [] } } }
    """.trimIndent()

    private fun driverStandingsBody() = """
        { "MRData": { "StandingsTable": { "StandingsLists": [{
          "DriverStandings": [{
            "position": "1", "points": "25", "wins": "1",
            "Driver": { "driverId": "max_verstappen", "permanentNumber": "1", "code": "VER", "givenName": "Max", "familyName": "Verstappen" },
            "Constructors": [{ "constructorId": "red_bull", "name": "Red Bull" }]
          }]
        }] } } }
    """.trimIndent()

    private class FixedClock(private val epochMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(epochMs)
    }
}
