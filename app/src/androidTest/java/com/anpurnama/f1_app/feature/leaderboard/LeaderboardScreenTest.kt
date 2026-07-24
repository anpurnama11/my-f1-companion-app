package com.anpurnama.f1_app.feature.leaderboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.ui.theme.F1appTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LeaderboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabs_showOnlySelectedStandingsAndKeepRowNavigation() {
        val viewModel = LeaderboardViewModel(
            getDriversStandings = { Outcome.Success(listOf(driver)) },
            getConstructorsStandings = { Outcome.Success(listOf(constructor)) },
        )
        var clickedDriverId: String? = null
        var clickedTeamId: String? = null

        composeRule.setContent {
            F1appTheme {
                LeaderboardScreen(
                    onDriverClick = { clickedDriverId = it },
                    onTeamClick = { clickedTeamId = it },
                    viewModel = viewModel,
                )
            }
        }

        waitForText("Andrea Kimi Antonelli")
        composeRule.onNodeWithText("Andrea Kimi Antonelli").assertIsDisplayed()
        composeRule.onAllNodesWithText("Mercedes Formula 1 Team").assertCountEquals(0)
        composeRule.onNodeWithText("Andrea Kimi Antonelli").performClick()
        assertEquals("antonelli", clickedDriverId)

        composeRule.onNodeWithTag("leaderboard-standings-pager").performTouchInput { swipeLeft() }
        waitForText("Mercedes Formula 1 Team")
        composeRule.onNodeWithText("Mercedes Formula 1 Team").assertIsDisplayed()
        composeRule.onAllNodesWithText("Andrea Kimi Antonelli").assertCountEquals(0)
        composeRule.onNodeWithText("Mercedes Formula 1 Team").performClick()
        assertEquals("mercedes", clickedTeamId)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private val driver = DriverStanding(
        driverId = "antonelli",
        teamId = "mercedes",
        position = 1,
        points = 204,
        wins = 6,
        driverName = "Andrea Kimi Antonelli",
        driverShortName = "ANT",
        driverNumber = 12,
    )

    private val constructor = ConstructorStanding(
        teamId = "mercedes",
        position = 1,
        points = 358,
        wins = 8,
        teamName = "Mercedes Formula 1 Team",
        country = "Germany",
    )
}
