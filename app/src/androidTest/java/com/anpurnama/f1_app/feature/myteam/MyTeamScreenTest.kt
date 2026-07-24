package com.anpurnama.f1_app.feature.myteam

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.ui.theme.F1appTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MyTeamScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun filledDriverSlot_opensPickerAndReplacesThatSlot() {
        var selectedSlot: DriverSlot? = null
        var selectedDriverId: String? = null
        composeRule.setContent {
            F1appTheme {
                MyTeamContent(
                    state = populatedState,
                    onSelectDriver = { slot, driverId ->
                        selectedSlot = slot
                        selectedDriverId = driverId
                    },
                    onSelectTeam = {},
                    onRetry = {},
                )
            }
        }

        assertNull(selectedSlot)
        composeRule.onNodeWithContentDescription("Change favorite Driver 1").performClick()
        composeRule.onNodeWithText("Choose Driver 1").assertIsDisplayed()
        composeRule.onNodeWithText("Lewis Hamilton").performClick()

        assertEquals(DriverSlot.Driver1, selectedSlot)
        assertEquals("hamilton", selectedDriverId)
        composeRule.onAllNodesWithText("Choose Driver 1").assertCountEquals(0)
    }

    @Test
    fun driverUsedByOtherSlot_isDisabledInPicker() {
        composeRule.setContent {
            F1appTheme {
                MyTeamContent(
                    state = populatedState,
                    onSelectDriver = { _, _ -> },
                    onSelectTeam = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Change favorite Driver 1").performClick()
        composeRule.onNodeWithText("Already selected").assertIsNotEnabled()
    }

    @Test
    fun filledConstructorSlot_opensPickerAndReplacesConstructor() {
        var selectedTeamId: String? = null
        composeRule.setContent {
            F1appTheme {
                MyTeamContent(
                    state = populatedState,
                    onSelectDriver = { _, _ -> },
                    onSelectTeam = { selectedTeamId = it },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Change favorite Constructor").performClick()
        composeRule.onNodeWithText("Choose Constructor").assertIsDisplayed()
        composeRule.onNodeWithText("Scuderia Ferrari").performClick()

        assertEquals("ferrari", selectedTeamId)
        composeRule.onAllNodesWithText("Choose Constructor").assertCountEquals(0)
    }

    private val populatedState = MyTeamViewModel.UiState(
        favorites = SectionUiState.Content(Favorites("antonelli", "russell", "mercedes")),
        drivers = SectionUiState.Content(
            listOf(
                driver("antonelli", "Andrea Kimi Antonelli", "ANT", 12),
                driver("russell", "George Russell", "RUS", 63),
                driver("hamilton", "Lewis Hamilton", "HAM", 44, teamId = "ferrari"),
            ),
        ),
        constructors = SectionUiState.Content(
            listOf(
                ConstructorStanding(
                    teamId = "mercedes",
                    position = 1,
                    points = 394,
                    wins = 10,
                    teamName = "Mercedes Formula 1 Team",
                    country = "Germany",
                ),
                ConstructorStanding(
                    teamId = "ferrari",
                    position = 2,
                    points = 350,
                    wins = 8,
                    teamName = "Scuderia Ferrari",
                    country = "Italy",
                ),
            ),
        ),
    )

    private fun driver(
        id: String,
        name: String,
        shortName: String,
        number: Int,
        teamId: String = "mercedes",
    ) = DriverStanding(
        driverId = id,
        teamId = teamId,
        position = 1,
        points = 0,
        wins = 0,
        driverName = name,
        driverShortName = shortName,
        driverNumber = number,
    )
}
