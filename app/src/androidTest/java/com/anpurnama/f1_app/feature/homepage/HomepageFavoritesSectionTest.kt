package com.anpurnama.f1_app.feature.homepage

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.ui.theme.F1appTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomepageFavoritesSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyFavorites_showPickFavoritesCta() {
        var clicked = false
        composeRule.setContent {
            F1appTheme {
                FavoritesSection(
                    favorites = Favorites(null, null, null),
                    drivers = emptyList(),
                    constructors = emptyList(),
                    onPickFavorites = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Pick favorites").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun populatedFavorites_showThreeFixedRows() {
        composeRule.setContent {
            F1appTheme {
                FavoritesSection(
                    favorites = Favorites("hamilton", "russell", "mercedes"),
                    drivers = listOf(
                        driver("hamilton", "mercedes", "Lewis Hamilton"),
                        driver("russell", "mercedes", "George Russell"),
                    ),
                    constructors = listOf(constructor("mercedes", "Mercedes")),
                    onPickFavorites = {},
                )
            }
        }

        composeRule.onNodeWithText("Driver 1").assertIsDisplayed()
        composeRule.onNodeWithText("Driver 2").assertIsDisplayed()
        composeRule.onNodeWithText("Constructor").assertIsDisplayed()
        composeRule.onNodeWithTag("favorite-accent-driver-1").assertIsDisplayed()
        composeRule.onNodeWithTag("favorite-accent-driver-2").assertIsDisplayed()
        composeRule.onNodeWithTag("favorite-accent-constructor").assertIsDisplayed()
    }

    @Test
    fun selectedButUnavailableFavorite_isNotShownAsAddPrompt() {
        composeRule.setContent {
            F1appTheme {
                FavoritesSection(
                    favorites = Favorites("retired-driver", null, null),
                    drivers = emptyList(),
                    constructors = emptyList(),
                    onPickFavorites = {},
                )
            }
        }

        composeRule.onNodeWithText("Unavailable").assertIsDisplayed()
        composeRule.onAllNodesWithText("Add a driver").assertCountEquals(0)
    }

    @Test
    fun unknownTeam_omitsAccentBar() {
        composeRule.setContent {
            F1appTheme {
                FavoritesSection(
                    favorites = Favorites("driver", null, "new_team"),
                    drivers = listOf(driver("driver", "new_team", "New Driver")),
                    constructors = listOf(constructor("new_team", "New Team")),
                    onPickFavorites = {},
                )
            }
        }

        composeRule.onAllNodesWithTag("favorite-accent-driver-1").assertCountEquals(0)
        composeRule.onAllNodesWithTag("favorite-accent-constructor").assertCountEquals(0)
    }

    private fun driver(id: String, teamId: String, name: String) = DriverStanding(
        driverId = id,
        teamId = teamId,
        position = 1,
        points = 100,
        wins = 1,
        driverName = name,
        driverShortName = id.take(3).uppercase(),
        driverNumber = 44,
    )

    private fun constructor(id: String, name: String) = ConstructorStanding(
        teamId = id,
        position = 1,
        points = 200,
        wins = 2,
        teamName = name,
        country = "United Kingdom",
    )
}
