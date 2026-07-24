package com.anpurnama.f1_app.feature.team

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.TeamDetail
import com.anpurnama.f1_app.test.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModelTest {

    @get:Rule
    val mainRule = MainCoroutineRule()

    @Test
    fun `team detail exposes failure and refresh retries with no cache`() = runTest {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val vm = TeamViewModel(
            teamId = "cadillac",
            getTeamDetail = { id, force ->
                calls += id to force
                if (force) {
                    Outcome.Success(
                        TeamDetail(
                            teamId = id, wordmark = "Cadillac Formula 1 Team",
                            country = "United States", firstAppearance = 2026,
                            constructorsChampionships = null, driversChampionships = null,
                            standing = null,
                        )
                    )
                } else {
                    Outcome.Failure("offline")
                }
            },
        )

        vm.uiState.take(2).toList()
        assertTrue((vm.uiState.value as TeamViewModel.UiState.Sections).detail is SectionUiState.Error)
        vm.refresh()
        testScheduler.advanceUntilIdle()
        val loaded = (vm.uiState.value as TeamViewModel.UiState.Sections).detail
        assertTrue(loaded is SectionUiState.Content)
        assertEquals(listOf("cadillac" to false, "cadillac" to true), calls)
    }
}
