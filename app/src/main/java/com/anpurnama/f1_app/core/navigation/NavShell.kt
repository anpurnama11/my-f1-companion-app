package com.anpurnama.f1_app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.anpurnama.f1_app.feature.homepage.HomepageScreen
import com.anpurnama.f1_app.feature.circuit.CircuitScreen
import com.anpurnama.f1_app.feature.driver.DriverScreen
import com.anpurnama.f1_app.feature.leaderboard.LeaderboardScreen
import com.anpurnama.f1_app.feature.myteam.MyTeamScreen
import com.anpurnama.f1_app.feature.round.RoundScreen
import com.anpurnama.f1_app.feature.sessionresult.SessionResultScreen
import com.anpurnama.f1_app.feature.schedule.ScheduleScreen
import com.anpurnama.f1_app.feature.team.TeamScreen

/**
 * The 4-tab app shell using Navigation 3's multi-backstack pattern.
 *
 * Each top-level tab (Homepage, Schedule, Leaderboard, MyTeam) owns a
 * persistent [NavBackStack] that is never cleared on tab switch.
 * This means ViewModels and composable state survive across tab switches
 * — no re-fetch of data when the user goes Home → Schedule → Home.
 *
 * Navigation actions go through a [Navigator] that dispatches to the
 * correct per-tab backstack (tab switch vs within-stack push/pop).
 *
 * **Exit-through-home:** [Route.Homepage] entries are always rendered.
 * Pressing back on another tab's root switches to Homepage; pressing
 * back on Homepage's root exits the app.
 */
@Composable
fun NavShell() {
    val navigationState = rememberNavigationState(
        startRoute = Route.Homepage,
        topLevelRoutes = Route.homepageTabs,
    )
    val navigator = remember(navigationState) { Navigator(navigationState) }

    val entryProvider = entryProvider {
        entry<Route.Homepage> {
            HomepageScreen(
                onPickFavorites = { navigator.navigate(Route.MyTeam) },
            )
        }
        entry<Route.Schedule> {
            ScheduleScreen(
                onRoundClick = { y, r -> navigator.navigate(Route.RoundDetail(y, r)) },
            )
        }
        entry<Route.Leaderboard> {
            LeaderboardScreen(
                onDriverClick = { id -> navigator.navigate(Route.DriverDetail(id)) },
                onTeamClick = { id -> navigator.navigate(Route.TeamDetail(id)) },
            )
        }
        entry<Route.MyTeam> { MyTeamScreen() }
        entry<Route.CircuitDetail> { key -> CircuitScreen(circuitId = key.circuitId) }
        entry<Route.RoundDetail> { key ->
            RoundScreen(
                year = key.year,
                round = key.round,
                onCircuitClick = { id -> navigator.navigate(Route.CircuitDetail(id)) },
                onSessionResultClick = { year, round, session ->
                    navigator.navigate(Route.SessionResult(year, round, session))
                },
            )
        }
        entry<Route.SessionResult> { key ->
            SessionResultScreen(
                year = key.year,
                round = key.round,
                session = key.session,
            )
        }
        entry<Route.DriverDetail> { key ->
            DriverScreen(
                driverId = key.driverId,
                onTeamClick = { id -> navigator.navigate(Route.TeamDetail(id)) },
            )
        }
        entry<Route.TeamDetail> { key -> TeamScreen(teamId = key.teamId) }
    }

    Scaffold(
        bottomBar = { F1BottomBar(navigationState) },
    ) { innerPadding ->
        NavDisplay(
            entries = navigationState.toDecoratedEntries(entryProvider),
            modifier = Modifier.padding(innerPadding),
            onBack = { navigator.goBack() },
        )
    }
}

@Composable
private fun F1BottomBar(navigationState: NavigationState) {
    NavigationBar {
        TopLevelDestination.entries.forEach { dest ->
            NavigationBarItem(
                selected = navigationState.currentRoute == dest.route,
                onClick = { navigationState.selectTab(dest.route) },
                icon = {
                    Text(
                        text = dest.glyph,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                label = { Text(dest.label) },
            )
        }
    }
}

private enum class TopLevelDestination(
    val route: Route,
    val label: String,
    val glyph: String,
) {
    Homepage(Route.Homepage, "Home", "H"),
    Schedule(Route.Schedule, "Schedule", "S"),
    Leaderboard(Route.Leaderboard, "Leaderboard", "L"),
    MyTeam(Route.MyTeam, "My Team", "M"),
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$title — coming soon",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
