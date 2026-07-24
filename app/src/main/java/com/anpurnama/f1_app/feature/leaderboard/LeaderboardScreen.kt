package com.anpurnama.f1_app.feature.leaderboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onDriverClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
    viewModel: LeaderboardViewModel = rememberLeaderboardViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = (state as? LeaderboardViewModel.UiState.Sections) ?: return
    val pagerState = rememberPagerState(pageCount = { LeaderboardTab.entries.size })
    val scope = rememberCoroutineScope()
    val isRefreshing = sections.drivers is SectionUiState.Loading ||
        sections.constructors is SectionUiState.Loading

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.normal)
                .padding(top = Spacing.normal),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
            ) {
                LeaderboardTab.entries.forEach { tab ->
                    Tab(
                        selected = pagerState.currentPage == tab.ordinal,
                        onClick = { scope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                        text = { Text(tab.label) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("leaderboard-standings-pager"),
            ) { page ->
                when (LeaderboardTab.entries[page]) {
                    LeaderboardTab.Drivers -> StandingsSection(
                        state = sections.drivers,
                        onRetry = viewModel::refresh,
                    ) { drivers ->
                        drivers.forEach { standing ->
                            DriverStandingRow(standing, onClick = { onDriverClick(standing.driverId) })
                        }
                    }
                    LeaderboardTab.Constructors -> StandingsSection(
                        state = sections.constructors,
                        onRetry = viewModel::refresh,
                    ) { constructors ->
                        constructors.forEach { standing ->
                            ConstructorStandingRow(standing, onClick = { onTeamClick(standing.teamId) })
                        }
                    }
                }
            }
        }
    }
}

private enum class LeaderboardTab(val label: String) {
    Drivers("Drivers"),
    Constructors("Constructors"),
}

@Composable
private fun <T> StandingsSection(
    state: SectionUiState<List<T>>,
    onRetry: () -> Unit,
    content: @Composable (List<T>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutcomeContent(state = state, onRetry = onRetry) { rows ->
            if (rows.isEmpty()) {
                Text("No current standings", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    content(rows)
                }
            }
        }
    }
}

@Composable
private fun DriverStandingRow(standing: DriverStanding, onClick: () -> Unit) {
    StandingCard(
        label = standing.driverName.ifBlank { standing.driverId },
        secondary = listOfNotNull(standing.driverShortName, standing.teamName).joinToString(" · "),
        position = standing.position,
        wins = standing.wins,
        points = standing.points,
        contentDescription = "Open driver ${standing.driverName}",
        onClick = onClick,
    )
}

@Composable
private fun ConstructorStandingRow(standing: ConstructorStanding, onClick: () -> Unit) {
    StandingCard(
        label = standing.teamName.ifBlank { standing.teamId },
        secondary = standing.country.orEmpty(),
        position = standing.position,
        wins = standing.wins,
        points = standing.points,
        contentDescription = "Open constructor ${standing.teamName}",
        onClick = onClick,
    )
}

@Composable
private fun StandingCard(
    label: String,
    secondary: String,
    position: Int,
    wins: Int,
    points: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.normal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.normal),
        ) {
            Text("P$position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (secondary.isNotBlank()) {
                    Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text("$points pts", fontWeight = FontWeight.SemiBold)
                Text("$wins wins", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun rememberLeaderboardViewModel(): LeaderboardViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(
        factory = leaderboardViewModelFactory(wiring.getDriversStandings, wiring.getConstructorsStandings),
    )
}
