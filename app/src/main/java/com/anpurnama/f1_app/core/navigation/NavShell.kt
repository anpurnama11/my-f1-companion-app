package com.anpurnama.f1_app.core.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.anpurnama.f1_app.R
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
fun NavShell(
    /**
     * Deep-link route pending consumption. Set by [MainActivity]
     * from `intent.data` (initial onCreate) or `onNewIntent`
     * (foreground widget tap). When non-null, a
     * [LaunchedEffect] pushes it onto the Homepage backstack and
     * invokes [onDeepLinkConsumed] to clear the pending state.
     */
    pendingDeepLink: Route? = null,
    /**
     * Callback that clears the parent's pending-deep-link state
     * once the route has been pushed. Wired by [MainActivity] to
     * set its own `pendingDeepLinkRoute` back to null.
     */
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navigationState = rememberNavigationState(
        startRoute = Route.Homepage,
        topLevelRoutes = Route.homepageTabs,
    )
    val navigator = remember(navigationState) { Navigator(navigationState) }

    // Deep-link consumption. The spec is "push RoundDetail onto
    // Homepage as backstack root ([Homepage, RoundDetail]); back
    // lands on Homepage". So we always switch to Homepage first
    // (preserves the user's other-tab state, e.g. an open Schedule
    // detail), then push the route onto Homepage's backstack.
    // The effect is keyed on `pendingDeepLink` so each new
    // intent triggers a fresh push.
    LaunchedEffect(pendingDeepLink) {
        val route = pendingDeepLink ?: return@LaunchedEffect
        if (route is Route.RoundDetail) {
            navigator.navigate(Route.Homepage)
            navigator.navigate(route)
        } else {
            navigator.navigate(route)
        }
        onDeepLinkConsumed()
    }

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
                    Icon(
                        painter = painterResource(dest.iconRes),
                        contentDescription = dest.label,
                    )
                },
                label = { Text(dest.label) },
                colors = NavigationBarItemDefaults.colors(
                    // Selected tab picks up the F1 primary orange so the
                    // current tab reads as "live" against the dark surface.
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                ),
            )
        }
    }
}

private enum class TopLevelDestination(
    val route: Route,
    val label: String,
    @DrawableRes val iconRes: Int,
) {
    Homepage(Route.Homepage, "Home", R.drawable.ic_home_outline),
    Schedule(Route.Schedule, "Schedule", R.drawable.ic_schedule_outline),
    Leaderboard(Route.Leaderboard, "Leaderboard", R.drawable.ic_leaderboard_outline),
    MyTeam(Route.MyTeam, "My Team", R.drawable.ic_myteam_outline),
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
