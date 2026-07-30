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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

class SeasonScheduleCacheRepositoryTest {
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
    fun `successful current schedule refresh promotes active season and writes snapshot`() = runTest {
        val (store, scope) = newStore()
        val repo = SeasonScheduleCacheRepository(store, client { jsonOk(scheduleBody(2026)) }, FixedClock(1_000))

        val result = repo.refreshCurrentSeason(RefreshReason.StaleOpen)

        assertEquals(RefreshResult.Refreshed, result)
        val state = store.state.first()
        assertEquals(2026, state.activeSeason)
        val snapshot = state.snapshots.getValue(CacheResourceKeys.currentSeasonSchedule(2026).value)
        assertEquals("season.schedule", snapshot.payloadKind)
        assertEquals(1_000L, snapshot.fetchedAtEpochMs)
        assertEquals(RefreshAttemptStatus.Succeeded, snapshot.lastAttemptStatus)
        val cached = repo.observeCurrentSeason().first()!!
        assertEquals(2026, cached.data.year)
        assertEquals("Bahrain GP", cached.data.races.single().name)
        scope.cancel()
    }

    @Test
    fun `failed refresh preserves last good payload and records failed attempt`() = runTest {
        val (store, scope) = newStore()
        val repo = SeasonScheduleCacheRepository(store, client { jsonOk(scheduleBody(2026)) }, FixedClock(1_000))
        repo.refreshCurrentSeason(RefreshReason.StaleOpen)

        val failing = SeasonScheduleCacheRepository(store, client { respondError(HttpStatusCode.ServiceUnavailable) }, FixedClock(2_000))
        val result = failing.refreshCurrentSeason(RefreshReason.PullToRefresh)

        assertEquals(RefreshResult.RetryableFailure("Server error (503)"), result)
        val cached = failing.observeCurrentSeason().first()!!
        assertEquals("Bahrain GP", cached.data.races.single().name)
        assertEquals(2_000L, cached.snapshot.lastAttemptEpochMs)
        assertEquals(RefreshAttemptStatus.Failed("Server error (503)"), cached.snapshot.lastAttemptStatus)
        scope.cancel()
    }

    @Test
    fun `new valid schedule promotes and prunes old season scoped snapshots atomically`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2025, snapshot(2025))
        store.writeSnapshot(snapshotFor(CacheResourceKeys.driverStandings(2025)))
        store.writeSnapshot(snapshotFor(CacheResourceKeys.circuitMetadata("monza")))
        val repo = SeasonScheduleCacheRepository(store, client { jsonOk(scheduleBody(2026)) }, FixedClock(1_000))

        repo.refreshCurrentSeason(RefreshReason.StaleOpen)

        val state = store.state.first()
        assertEquals(2026, state.activeSeason)
        assertTrue(CacheResourceKeys.driverStandings(2025).value !in state.snapshots)
        assertTrue(CacheResourceKeys.circuitMetadata("monza").value in state.snapshots)
        assertTrue(CacheResourceKeys.currentSeasonSchedule(2026).value in state.snapshots)
        scope.cancel()
    }

    @Test
    fun `overlapping refreshes share one network call`() = runTest {
        val (store, scope) = newStore()
        var calls = 0
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        val repo = SeasonScheduleCacheRepository(store, client {
            calls++
            requestStarted.complete(Unit)
            releaseRequest.await()
            jsonOk(scheduleBody(2026))
        }, FixedClock(1_000), scope = backgroundScope)

        val first = async { repo.refreshCurrentSeason(RefreshReason.StaleOpen) }
        requestStarted.await()
        val second = async { repo.refreshCurrentSeason(RefreshReason.Periodic) }
        releaseRequest.complete(Unit)

        assertEquals(RefreshResult.Refreshed, first.await())
        assertEquals(RefreshResult.Refreshed, second.await())
        assertEquals(1, calls)
        scope.cancel()
    }

    @Test
    fun `fresh TTL skip is reported without a network request`() = runTest {
        val (store, scope) = newStore()
        store.promoteActiveSeason(2026, snapshot(2026).copy(staleAfterEpochMs = 2_000L))
        var calls = 0
        val repo = SeasonScheduleCacheRepository(store, client {
            calls++
            jsonOk(scheduleBody(2026))
        }, FixedClock(1_000))

        val result = repo.refreshCurrentSeason(RefreshReason.Periodic)

        assertEquals(RefreshResult.SkippedFresh, result)
        assertEquals(0, calls)
        scope.cancel()
    }

    @Test
    fun `HTTP 404 is permanent while HTTP 408 is retryable`() = runTest {
        val (store, scope) = newStore()
        val permanent = SeasonScheduleCacheRepository(
            store,
            client { respondError(HttpStatusCode.NotFound) },
            FixedClock(1_000),
        )
        val retryable = SeasonScheduleCacheRepository(
            store,
            client { respondError(HttpStatusCode.RequestTimeout) },
            FixedClock(2_000),
        )

        assertEquals(
            RefreshResult.PermanentFailure("Request failed (404)"),
            permanent.refreshCurrentSeason(RefreshReason.PullToRefresh),
        )
        assertEquals(
            RefreshResult.RetryableFailure("Request failed (408)"),
            retryable.refreshCurrentSeason(RefreshReason.PullToRefresh),
        )
        scope.cancel()
    }

    private fun scheduleBody(season: Int) = """
        { "season": $season,
          "races": [{
            "round": 1,
            "raceName": "Bahrain GP",
            "circuit": { "circuitId": "bahrain", "circuitName": "Bahrain", "circuitLength": "5412km" },
            "schedule": { "race": { "date": "$season-03-02", "time": "15:00:00Z" } }
          }]
        }
    """.trimIndent()

    private fun snapshot(season: Int) = ResourceSnapshot(
        key = CacheResourceKeys.currentSeasonSchedule(season).value,
        season = season,
        payloadKind = "season.schedule",
        payloadVersion = 1,
        payloadJson = scheduleBody(season),
        fetchedAtEpochMs = 1L,
        staleAfterEpochMs = 2L,
    )

    private fun snapshotFor(key: com.anpurnama.f1_app.core.cache.CacheResourceKey) = ResourceSnapshot(
        key = key.value,
        season = key.season,
        payloadKind = key.payloadKind,
        payloadVersion = 1,
        payloadJson = "{}",
        fetchedAtEpochMs = 1L,
        staleAfterEpochMs = 2L,
    )

    private class FixedClock(private val epochMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(epochMs)
    }
}
