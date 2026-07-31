package com.anpurnama.f1_app.feature.sessionresult

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.ui.ContentSyncStatus
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.FastestPitstop
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.SessionResult
import com.anpurnama.f1_app.f1.model.SessionType
import com.anpurnama.f1_app.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionResultViewModelTest {

    @get:Rule
    val mainRule = MainCoroutineRule()

    private val RACE_RESULT = SessionResult(
        year = 2026, round = 1, raceName = "Bahrain GP",
        circuit = Circuit("bahrain", "Bahrain International Circuit", "5412km", 15, "Sakhir", "Bahrain"),
        session = SessionType.Race,
        raceResults = listOf(
            RoundResult("1", 25, "1", "1:24:04.252", "max_verstappen", "Max Verstappen", "VER", 1, "red_bull", "Red Bull"),
            RoundResult("2", 18, "3", "+22.457", "hamilton", "Lewis Hamilton", "HAM", 44, "mercedes", "Mercedes"),
            RoundResult("3", 15, "2", "+45.000", "leclerc", "Charles Leclerc", "LEC", 16, "ferrari", "Ferrari"),
        ),
    )

    private val PITSTOP = FastestPitstop("max_verstappen", 21.5)

    private fun snapshot(payloadKind: String, staleAfter: Long = Long.MAX_VALUE) = ResourceSnapshot(
        key = "fake:$payloadKind",
        season = 2026,
        payloadKind = payloadKind,
        payloadVersion = 1,
        payloadJson = "{}",
        fetchedAtEpochMs = 1L,
        staleAfterEpochMs = staleAfter,
        lastAttemptEpochMs = 1L,
        lastAttemptStatus = RefreshAttemptStatus.Succeeded,
    )

    @Test
    fun `init-less loads session result via use case and reaches Content`() = runTest {
        val vm = SessionResultViewModel(
            year = 2026, round = 1, session = SessionType.Race,
            getSessionResult = { _, _, _, _ -> Outcome.Success(RACE_RESULT) },
            getFastestPitstop = { _, _, _ -> Outcome.Success(PITSTOP) },
        )

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val sections = vm.uiState.value
        assertTrue(sections.result is SectionUiState.Content)
        assertEquals("Max Verstappen",
            ((sections.result as SectionUiState.Content).data.raceResults.first().driverName))
        assertTrue(sections.pitstop is SectionUiState.Content)
        assertEquals("max_verstappen",
            ((sections.pitstop as SectionUiState.Content).data?.driverId))
    }

    @Test
    fun `cache observer seeds Content from snapshot on warmUp`() = runTest {
        val cachedResult = MutableStateFlow<CachedResource<SessionResult>?>(null)
        val vm = SessionResultViewModel(
            year = 2026, round = 1, session = SessionType.Race,
            getSessionResult = { _, _, _, _ -> Outcome.Failure("should not be called") },
            getFastestPitstop = null,
            observeCachedResult = cachedResult,
            refreshCachedResult = { RefreshResult.Refreshed },
        )
        cachedResult.value = CachedResource(RACE_RESULT, snapshot("session-results.race"))

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val sections = vm.uiState.value
        assertTrue(sections.result is SectionUiState.Content)
        assertEquals("Max Verstappen",
            ((sections.result as SectionUiState.Content).data.raceResults.first().driverName))
    }

    @Test
    fun `cache refresh failure preserves existing content with RefreshFailed sync`() = runTest {
        val cachedResult = MutableStateFlow<CachedResource<SessionResult>?>(null)
        var capturedReason: RefreshReason? = null
        val vm = SessionResultViewModel(
            year = 2026, round = 1, session = SessionType.Race,
            getSessionResult = { _, _, _, _ -> Outcome.Failure("fallback should not be called") },
            getFastestPitstop = null,
            observeCachedResult = cachedResult,
            refreshCachedResult = { reason ->
                capturedReason = reason
                RefreshResult.RetryableFailure("network error")
            },
        )
        // Seed stale cached content
        cachedResult.value = CachedResource(RACE_RESULT, snapshot("session-results.race", staleAfter = 1L))
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        // refresh should try to update but fail, preserving content
        vm.refresh()
        testScheduler.advanceUntilIdle()

        val sections = vm.uiState.value
        val resultContent = sections.result as? SectionUiState.Content
            ?: throw AssertionError("expected Content, got ${sections.result}")
        assertEquals(ContentSyncStatus.RefreshFailed("network error"), resultContent.sync)
        assertEquals("Max Verstappen", resultContent.data.raceResults.first().driverName)
        assertEquals(RefreshReason.PullToRefresh, capturedReason)
    }

    @Test
    fun `pitstop retryable failure preserves cached enrichment with RefreshFailed sync`() = runTest {
        val cachedPitstop = MutableStateFlow<CachedResource<FastestPitstop?>?>(
            CachedResource(PITSTOP, snapshot("session-results.pitstops", staleAfter = 1L)),
        )
        val vm = SessionResultViewModel(
            year = 2026, round = 1, session = SessionType.Race,
            getSessionResult = { _, _, _, _ -> Outcome.Success(RACE_RESULT) },
            getFastestPitstop = { _, _, _ -> Outcome.Failure("fallback should not be called") },
            observeCachedPitstop = cachedPitstop,
            refreshCachedPitstop = { RefreshResult.RetryableFailure("offline") },
        )

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        vm.refresh()
        testScheduler.advanceUntilIdle()

        val content = vm.uiState.value.pitstop as? SectionUiState.Content
            ?: throw AssertionError("cached pitstop should remain Content")
        assertEquals(PITSTOP, content.data)
        assertEquals(ContentSyncStatus.RefreshFailed("offline"), content.sync)
    }

    @Test
    fun `historical season with cache fallback falls through to direct network`() = runTest {
        val cachedResult = MutableStateFlow<CachedResource<SessionResult>?>(null)
        var directNetworkCalled = false
        val vm = SessionResultViewModel(
            year = 2025, round = 1, session = SessionType.Race,
            getSessionResult = { _, _, _, _ ->
                directNetworkCalled = true
                Outcome.Success(RACE_RESULT.copy(year = 2025))
            },
            getFastestPitstop = null,
            observeCachedResult = cachedResult,
            refreshCachedResult = { RefreshResult.PermanentFailure("Not the active season") },
        )

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        assertTrue("Direct network should be called as fallback", directNetworkCalled)
        val sections = vm.uiState.value
        assertTrue(sections.result is SectionUiState.Content)
        assertEquals(2025, ((sections.result as SectionUiState.Content).data.year))
    }

    @Test
    fun `unrelated permanent failure does not trigger historical direct fallback`() = runTest {
        var directNetworkCalled = false
        val vm = SessionResultViewModel(
            year = 2026, round = 1, session = SessionType.Race,
            getSessionResult = { _, _, _, _ ->
                directNetworkCalled = true
                Outcome.Success(RACE_RESULT)
            },
            getFastestPitstop = null,
            observeCachedResult = MutableStateFlow<CachedResource<SessionResult>?>(null),
            refreshCachedResult = { RefreshResult.PermanentFailure("Request failed (404)") },
        )

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        assertTrue("404 must not route around the cache repository", !directNetworkCalled)
        assertEquals(
            "Request failed (404)",
            (vm.uiState.value.result as SectionUiState.Error).message,
        )
    }

    @Test
    fun `deferred refresh preserves existing cached session content`() = runTest {
        val cachedResult = MutableStateFlow<CachedResource<SessionResult>?>(
            CachedResource(RACE_RESULT, snapshot("session-results.race", staleAfter = 1L)),
        )
        val vm = SessionResultViewModel(
            year = 2026, round = 1, session = SessionType.Race,
            getSessionResult = { _, _, _, _ -> Outcome.Failure("fallback should not be called") },
            getFastestPitstop = null,
            observeCachedResult = cachedResult,
            refreshCachedResult = { RefreshResult.Deferred },
        )

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val content = vm.uiState.value.result as? SectionUiState.Content
            ?: throw AssertionError("cached session should remain Content")
        assertEquals("Max Verstappen", content.data.raceResults.first().driverName)
        assertEquals(ContentSyncStatus.Stale, content.sync)
    }

    @Test
    fun `deferred cached refresh shows unavailable Error without direct fallback`() = runTest {
        var directNetworkCalled = false
        val vm = SessionResultViewModel(
            year = 2026, round = 1, session = SessionType.Race,
            getSessionResult = { _, _, _, _ ->
                directNetworkCalled = true
                Outcome.Success(RACE_RESULT)
            },
            getFastestPitstop = null,
            observeCachedResult = MutableStateFlow<CachedResource<SessionResult>?>(null),
            refreshCachedResult = { RefreshResult.Deferred },
        )

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val sections = vm.uiState.value
        assertTrue("direct network fallback must not bypass completion gate", !directNetworkCalled)
        assertTrue(sections.result is SectionUiState.Error)
        assertEquals("Session not yet complete", (sections.result as SectionUiState.Error).message)
    }

    @Test
    fun `non-race session omits pitstop section`() = runTest {
        val vm = SessionResultViewModel(
            year = 2026, round = 1, session = SessionType.Quali,
            getSessionResult = { _, _, _, _ -> Outcome.Success(RACE_RESULT.copy(session = SessionType.Quali)) },
            getFastestPitstop = { _, _, _ -> Outcome.Success(PITSTOP) },
        )

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val sections = vm.uiState.value
        assertTrue(sections.result is SectionUiState.Content)
        // Pitstop should be Content(null) for non-race sessions
        assertTrue(sections.pitstop is SectionUiState.Content)
        assertEquals(null, (sections.pitstop as SectionUiState.Content).data)
    }
}
