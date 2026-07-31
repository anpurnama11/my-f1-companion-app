package com.anpurnama.f1_app.feature.circuit

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.ResourceSnapshot
import com.anpurnama.f1_app.core.ui.ContentSyncStatus
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.CircuitDetail
import com.anpurnama.f1_app.f1.model.CircuitMostWins
import com.anpurnama.f1_app.f1.model.LapRecord
import com.anpurnama.f1_app.f1.model.MostWinningDriver
import com.anpurnama.f1_app.f1.model.MostWinningTeam
import com.anpurnama.f1_app.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CircuitViewModelTest {

    @get:Rule
    val mainRule = MainCoroutineRule()

    private val METADATA = CircuitDetail(
        id = "bahrain",
        name = "Bahrain International Circuit",
        country = "Bahrain",
        city = "Sakhir",
        circuitLengthKm = 5.412,
        numberOfCorners = 15,
        firstParticipationYear = 2004,
        lapRecord = LapRecord(
            time = "1:31:447",
            driverId = "pedro_de_la_rosa",
            teamId = "mclaren",
            year = 2005,
        ),
    )

    private val WINS = CircuitMostWins(
        topDriver = MostWinningDriver(
            driverId = "hamilton",
            name = "Lewis Hamilton",
            wins = 5,
        ),
        topTeam = MostWinningTeam(
            teamId = "ferrari",
            name = "Ferrari",
            wins = 7,
        ),
        totalRaces = 22,
    )

    private fun snapshot(
        payloadKind: String,
        staleAfter: Long = Long.MAX_VALUE,
    ) = ResourceSnapshot(
        key = "fake:$payloadKind",
        season = null,
        payloadKind = payloadKind,
        payloadVersion = 1,
        payloadJson = "{}",
        fetchedAtEpochMs = 1L,
        staleAfterEpochMs = staleAfter,
        lastAttemptEpochMs = 1L,
        lastAttemptStatus = RefreshAttemptStatus.Succeeded,
    )

    private fun fakeVm(
        circuitId: String = "bahrain",
        getCircuit: suspend (String, Boolean) -> Outcome<CircuitDetail> = { _, _ -> Outcome.Success(METADATA) },
        getCircuitMostWins: suspend (String, Boolean) -> Outcome<CircuitMostWins> = { _, _ -> Outcome.Success(WINS) },
    ) = CircuitViewModel(
        circuitId = circuitId,
        getCircuit = getCircuit,
        getCircuitMostWins = getCircuitMostWins,
    )

    @Test
    fun `init-less first load fires both use cases and reaches Content`() = runTest {
        val metadataCalls = mutableListOf<Pair<String, Boolean>>()
        val winsCalls = mutableListOf<Pair<String, Boolean>>()
        val vm = fakeVm(
            circuitId = "bahrain",
            getCircuit = { id, force ->
                metadataCalls += id to force
                Outcome.Success(METADATA)
            },
            getCircuitMostWins = { id, force ->
                winsCalls += id to force
                Outcome.Success(WINS)
            },
        )

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val loaded = vm.uiState.value as CircuitViewModel.UiState.Sections
        assertTrue(loaded.metadata is SectionUiState.Content)
        assertTrue(loaded.mostWins is SectionUiState.Content)
        // The id is forwarded on every call
        assertEquals(listOf("bahrain" to false), metadataCalls)
        assertEquals(listOf("bahrain" to false), winsCalls)
    }

    @Test
    fun `section independence — metadata failure leaves most-wins Content`() = runTest {
        val vm = fakeVm(
            getCircuit = { _, _ -> Outcome.Failure("offline") },
            getCircuitMostWins = { _, _ -> Outcome.Success(WINS) },
        )
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val loaded = vm.uiState.value as CircuitViewModel.UiState.Sections
        assertTrue(loaded.metadata is SectionUiState.Error)
        assertEquals("offline", (loaded.metadata as SectionUiState.Error).message)
        assertTrue(loaded.mostWins is SectionUiState.Content)
    }

    @Test
    fun `section independence — most-wins failure leaves metadata Content`() = runTest {
        val vm = fakeVm(
            getCircuit = { _, _ -> Outcome.Success(METADATA) },
            getCircuitMostWins = { _, _ -> Outcome.Failure("circuit not in jolpica namespace") },
        )
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val loaded = vm.uiState.value as CircuitViewModel.UiState.Sections
        assertTrue(loaded.metadata is SectionUiState.Content)
        assertTrue(loaded.mostWins is SectionUiState.Error)
        assertEquals("circuit not in jolpica namespace", (loaded.mostWins as SectionUiState.Error).message)
    }

    @Test
    fun `cache observer seeds Content from snapshot on warmUp`() = runTest {
        val cachedFlow = MutableStateFlow<CachedResource<CircuitDetail>?>(null)
        val vm = CircuitViewModel(
            circuitId = "bahrain",
            getCircuit = { _, _ -> Outcome.Failure("should not be called") },
            getCircuitMostWins = { _, _ -> Outcome.Failure("should not be called") },
            observeCachedMetadata = cachedFlow,
            refreshCachedMetadata = { RefreshResult.Refreshed },
            observeCachedMostWins = null,
            refreshCachedMostWins = null,
        )
        // Seed cached data before warmUp reads the flow
        cachedFlow.value = CachedResource(METADATA, snapshot("circuit.metadata"))

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val loaded = vm.uiState.value as CircuitViewModel.UiState.Sections
        assertTrue(loaded.metadata is SectionUiState.Content)
        assertEquals("Bahrain International Circuit",
            (loaded.metadata as SectionUiState.Content).data.name)
        // No cached mostWins → falls through to network use case which returns Failure
        assertTrue(loaded.mostWins is SectionUiState.Error)
    }

    @Test
    fun `cache refresh failure preserves cached content with RefreshFailed sync`() = runTest {
        val cachedFlow = MutableStateFlow<CachedResource<CircuitDetail>?>(null)
        var refreshResultValue: RefreshResult = RefreshResult.Refreshed
        var refreshReason: RefreshReason = RefreshReason.StaleOpen
        val vm = CircuitViewModel(
            circuitId = "bahrain",
            getCircuit = { _, _ -> Outcome.Failure("fallback should not be called") },
            getCircuitMostWins = { _, _ -> Outcome.Success(WINS) },
            observeCachedMetadata = cachedFlow,
            refreshCachedMetadata = { reason ->
                refreshReason = reason
                refreshResultValue
            },
            observeCachedMostWins = null,
            refreshCachedMostWins = null,
        )
        cachedFlow.value = CachedResource(METADATA, snapshot("circuit.metadata", staleAfter = 1L))
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        // Now trigger a stale-refresh that fails
        refreshResultValue = RefreshResult.RetryableFailure("offline")
        vm.refresh()
        testScheduler.advanceUntilIdle()

        val loaded = vm.uiState.value as CircuitViewModel.UiState.Sections
        val metaContent = loaded.metadata as? SectionUiState.Content ?: throw AssertionError("expected Content")
        assertEquals(ContentSyncStatus.RefreshFailed("offline"), metaContent.sync)
        assertEquals("Bahrain International Circuit", metaContent.data.name)
        assertEquals(RefreshReason.PullToRefresh, refreshReason)
    }

    @Test
    fun `cached sections remain independent when only metadata refresh fails`() = runTest {
        val cachedMetadata = MutableStateFlow<CachedResource<CircuitDetail>?>(
            CachedResource(METADATA, snapshot("circuit.metadata")),
        )
        val cachedMostWins = MutableStateFlow<CachedResource<CircuitMostWins>?>(
            CachedResource(WINS, snapshot("circuit.most-wins")),
        )
        val vm = CircuitViewModel(
            circuitId = "bahrain",
            getCircuit = { _, _ -> Outcome.Failure("metadata fallback should not be called") },
            getCircuitMostWins = { _, _ -> Outcome.Failure("most-wins fallback should not be called") },
            observeCachedMetadata = cachedMetadata,
            refreshCachedMetadata = { RefreshResult.RetryableFailure("metadata offline") },
            observeCachedMostWins = cachedMostWins,
            refreshCachedMostWins = { RefreshResult.Refreshed },
        )

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        vm.refresh()
        testScheduler.advanceUntilIdle()

        val loaded = vm.uiState.value as CircuitViewModel.UiState.Sections
        val metadata = loaded.metadata as SectionUiState.Content
        val mostWins = loaded.mostWins as SectionUiState.Content
        assertEquals(ContentSyncStatus.RefreshFailed("metadata offline"), metadata.sync)
        assertEquals(METADATA, metadata.data)
        assertEquals(ContentSyncStatus.Fresh, mostWins.sync)
        assertEquals(WINS, mostWins.data)
    }

    @Test
    fun `both failures surface as two independent errors — one screen, two empty cells`() = runTest {
        val vm = fakeVm(
            getCircuit = { _, _ -> Outcome.Failure("offline") },
            getCircuitMostWins = { _, _ -> Outcome.Failure("offline") },
        )
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()

        val loaded = vm.uiState.value as CircuitViewModel.UiState.Sections
        assertTrue(loaded.metadata is SectionUiState.Error)
        assertTrue(loaded.mostWins is SectionUiState.Error)
    }

    @Test
    fun `refresh re-fires both use cases with forceRefresh true`() = runTest {
        val metadataCalls = mutableListOf<Boolean>()
        val winsCalls = mutableListOf<Boolean>()
        val vm = fakeVm(
            getCircuit = { _, force -> metadataCalls += force; Outcome.Success(METADATA) },
            getCircuitMostWins = { _, force -> winsCalls += force; Outcome.Success(WINS) },
        )

        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(false, true), metadataCalls)
        assertEquals(listOf(false, true), winsCalls)
    }

    @Test
    fun `circuitId is forwarded to both use cases on every call`() = runTest {
        val seen = mutableListOf<String>()
        val vm = fakeVm(
            circuitId = "monza",
            getCircuit = { id, _ -> seen += id; Outcome.Success(METADATA.copy(id = id)) },
            getCircuitMostWins = { id, _ -> seen += id; Outcome.Success(WINS) },
        )
        vm.uiState.take(2).toList()
        testScheduler.advanceUntilIdle()
        vm.refresh()
        testScheduler.advanceUntilIdle()

        // 2 calls on warmup + 2 on refresh = 4
        assertEquals(listOf("monza", "monza", "monza", "monza"), seen)
        val loaded = vm.uiState.value as CircuitViewModel.UiState.Sections
        assertNotNull((loaded.metadata as SectionUiState.Content).data.id == "monza")
    }
}
