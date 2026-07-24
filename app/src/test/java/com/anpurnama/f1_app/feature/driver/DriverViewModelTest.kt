package com.anpurnama.f1_app.feature.driver

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.DriverDetail
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
class DriverViewModelTest {

    @get:Rule
    val mainRule = MainCoroutineRule()

    @Test
    fun `driver id is loaded and refresh bypasses cache`() = runTest {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val detail = DriverDetail(
            driverId = "antonelli", name = "Andrea Kimi Antonelli", shortName = "ANT",
            nationality = "Italy", birthday = "2006-08-25", number = 12,
            teamId = "mercedes", teamName = "Mercedes Formula 1 Team", standing = null,
        )
        val vm = DriverViewModel(
            driverId = "antonelli",
            getDriverDetail = { id, force -> calls += id to force; Outcome.Success(detail) },
        )

        vm.uiState.take(2).toList()
        val loaded = vm.uiState.value as DriverViewModel.UiState.Sections
        assertTrue(loaded.detail is SectionUiState.Content)
        assertEquals("antonelli", (loaded.detail as SectionUiState.Content).data.driverId)

        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("antonelli" to false, "antonelli" to true), calls)
    }
}
