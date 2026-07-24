package com.anpurnama.f1_app.feature.schedule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.RoundPodium
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.ui.theme.F1appTheme
import org.junit.Rule
import org.junit.Test

class ScheduleScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun swipeLeft_switchesFromUpcomingToPast() {
        val viewModel = ScheduleViewModel(
            getSeason = { Outcome.Success(season) },
            getRoundPodium = { _, _, _ -> Outcome.Success(RoundPodium(emptyList())) },
        )

        composeRule.setContent {
            F1appTheme {
                ScheduleScreen(
                    onRoundClick = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        waitForText("Hungarian GP")
        composeRule.onNodeWithText("Hungarian GP").assertIsDisplayed()

        composeRule.onNodeWithTag("schedule-tab-pager").performTouchInput { swipeLeft() }
        waitForText("Bahrain GP")
        composeRule.onNodeWithText("Bahrain GP").assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private val season = Season(
        year = 2026,
        races = listOf(
            Race(
                round = 1,
                name = "Bahrain GP",
                circuit = Circuit(
                    id = "bahrain",
                    name = "Bahrain",
                    circuitLengthRaw = "5412km",
                    corners = 15,
                    city = "Sakhir",
                    country = "Bahrain",
                ),
                winnerId = "verstappen",
                laps = 57,
            ),
            Race(
                round = 11,
                name = "Hungarian GP",
                circuit = Circuit(
                    id = "hungaroring",
                    name = "Hungaroring",
                    circuitLengthRaw = "4381km",
                    corners = 14,
                    city = "Mogyorod",
                    country = "Hungary",
                ),
                winnerId = null,
                laps = 70,
            ),
        ),
        completedGp = 1,
        totalKm = 5.412,
        totalLaps = 57,
        progressPercent = 0.5f,
    )
}
