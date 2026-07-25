package com.anpurnama.f1_app.feature.circuit

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.CircuitDetail
import com.anpurnama.f1_app.f1.model.CircuitMostWins
import com.anpurnama.f1_app.f1.model.LapRecord
import com.anpurnama.f1_app.f1.model.MostWinningDriver
import com.anpurnama.f1_app.f1.model.MostWinningTeam
import com.anpurnama.f1_app.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
