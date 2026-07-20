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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.anpurnama.f1_app.feature.homepage.HomepageScreen

/**
 * The 4-tab app shell. Only [Route.Homepage] renders real content this
 * slice — Schedule, Leaderboard, and MyTeam are placeholders that later
 * slices replace (ticket 02, 03, 05).
 *
 * Navigation 3 1.1.4 surface used here:
 *  - [rememberNavBackStack] → persistent [NavBackStack] backed by
 *    reflection serialization (Android-only overload).
 *  - [NavDisplay] with the backStack + an [entryProvider] whose
 *    `entry<T>` matches by reified KClass.
 *  - System back is wired by passing `onBack` that pops the stack.
 *
 * The bottom bar mutates `backStack` directly: a tap on a non-current
 * tab clears the stack and pushes the new top-level route. Detail
 * routes (when they land in ticket 05) push on top without clearing.
 */
@Composable
fun NavShell() {
    val backStack = rememberNavBackStack(Route.Homepage)

    Scaffold(
        bottomBar = { F1BottomBar(backStack) },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Route.Homepage> { HomepageScreen() }
                entry<Route.Schedule> { PlaceholderScreen("Schedule") }
                entry<Route.Leaderboard> { PlaceholderScreen("Leaderboard") }
                entry<Route.MyTeam> { PlaceholderScreen("My Team") }
            },
        )
    }
}

@Composable
private fun F1BottomBar(backStack: NavBackStack<NavKey>) {
    val current = backStack.lastOrNull()
    NavigationBar {
        TopLevelDestination.entries.forEach { dest ->
            NavigationBarItem(
                selected = current == dest.route,
                onClick = {
                    if (current != dest.route) {
                        backStack.clear()
                        backStack.add(dest.route)
                    }
                },
                // ponytail: text glyph stand-in; swap for Material vector
                // icons when the icons-extended dep lands.
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
