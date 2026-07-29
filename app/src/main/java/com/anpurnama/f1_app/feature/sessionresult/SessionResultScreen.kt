package com.anpurnama.f1_app.feature.sessionresult

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.FastestPitstop
import com.anpurnama.f1_app.f1.model.FastestLap
import com.anpurnama.f1_app.f1.model.PracticeResult
import com.anpurnama.f1_app.f1.model.QualifyingSegment
import com.anpurnama.f1_app.f1.model.QualifyingSegmentResult
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.SessionResult
import com.anpurnama.f1_app.f1.model.SessionType
import com.anpurnama.f1_app.f1.model.displayGrid
import com.anpurnama.f1_app.f1.model.displayStatusOrTime
import com.anpurnama.f1_app.f1.model.driverForPitstop
import com.anpurnama.f1_app.f1.model.positionChange
import com.anpurnama.f1_app.f1.model.toQualifyingSegmentTabs
import com.anpurnama.f1_app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionResultScreen(
    year: Int,
    round: Int,
    session: SessionType,
    viewModel: SessionResultViewModel = rememberSessionResultViewModel(year, round, session),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing = state.result is SectionUiState.Loading || state.pitstop is SectionUiState.Loading
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = viewModel::refresh) {
        OutcomeContent(state = state.result, onRetry = viewModel::refresh) { result ->
            SessionResultContent(result, state.pitstop)
        }
    }
}

@Composable
private fun SessionResultContent(
    result: SessionResult,
    pitstop: SectionUiState<FastestPitstop?>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.normal),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.normal),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Round ${result.round}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(result.raceName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(result.session.label, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (result.session == SessionType.Race || result.session == SessionType.Sprint) {
            item { Podium(result.raceResults.take(3)) }
            result.fastestLap?.let { lap -> item { Standout("Fastest lap", lap.driverShortName ?: lap.driverName, lap.time) } }
            if (result.session == SessionType.Race) {
                (pitstop as? SectionUiState.Content)?.data?.let { stop ->
                    item { PitstopCard(stop, result) }
                }
            }
            items(result.raceResults) { RaceResultRow(it) }
        } else if (result.session == SessionType.Quali || result.session == SessionType.SprintQuali) {
            item { QualifyingSegmentTabs(result) }
        } else {
            items(result.practiceResults) { PracticeRow(it) }
        }
    }
}

@Composable
private fun Podium(results: List<RoundResult>) {
    if (results.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Podium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            results.forEachIndexed { index, result ->
                Text("P${index + 1} ${result.driverShortName ?: result.driverName}",
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Standout(title: String, driver: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(driver, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PitstopCard(stop: FastestPitstop, result: SessionResult) {
    val driver = result.driverForPitstop(stop)
    Standout("Fastest pit-stop duration", driver?.driverShortName ?: stop.driverId, "%.3f s".format(stop.durationSeconds))
}

@Composable
private fun RaceResultRow(result: RoundResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("P${result.position}", fontWeight = FontWeight.SemiBold)
                Text("${result.points} pts", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(result.driverName, fontWeight = FontWeight.SemiBold)
            Text(result.teamName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Grid ${result.displayGrid()}${result.positionChange()?.let { delta ->
                    when {
                        delta > 0 -> " · ↑$delta"
                        delta < 0 -> " · ↓${-delta}"
                        else -> " · —"
                    }
                } ?: ""}", style = MaterialTheme.typography.bodySmall)
                Text(result.displayStatusOrTime(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun QualifyingSegmentTabs(result: SessionResult) {
    val tabs = remember(result.qualifyingResults) { result.qualifyingResults.toQualifyingSegmentTabs() }
    var selectedTabIndex by rememberSaveable(result.session, result.year, result.round) { mutableIntStateOf(0) }
    val selectedTab = tabs.getOrNull(selectedTabIndex) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SecondaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(tab.segment.shortLabel) },
                )
            }
        }
        Text(
            qualifyingSegmentSummary(selectedTab.segment.shortLabel, selectedTab.advancedCount, selectedTab.eliminatedCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        selectedTab.rows.forEach { row ->
            QualifyingSegmentRow(row)
        }
    }
}

private fun qualifyingSegmentSummary(segment: String, advancedCount: Int, eliminatedCount: Int): String =
    when (segment) {
        "Q3" -> "$segment · $advancedCount classified"
        else -> "$segment · $advancedCount advanced · $eliminatedCount eliminated"
    }

@Composable
private fun QualifyingSegmentRow(result: QualifyingSegmentResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("P${result.segmentPosition}", fontWeight = FontWeight.SemiBold)
                Text(result.displayTime(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(result.driverName, fontWeight = FontWeight.SemiBold)
            Text(result.teamName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    result.segmentOutcomeLabel(),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Overall P${result.overallPosition}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun QualifyingSegmentResult.segmentOutcomeLabel(): String = when {
    eliminated -> "Eliminated in ${segment.shortLabel}"
    segment == QualifyingSegment.Q3 -> "Final segment"
    else -> "Advanced"
}

private fun QualifyingSegmentResult.displayTime(): String =
    if (time.isNullOrBlank()) "No time" else time

@Composable
private fun PracticeRow(result: PracticeResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(Spacing.normal), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("P${result.position} ${result.driverShortName ?: result.driverName}", fontWeight = FontWeight.SemiBold)
            Text(result.time ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun rememberSessionResultViewModel(year: Int, round: Int, session: SessionType): SessionResultViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(factory = wiring.sessionResultViewModelFactory(year, round, session))
}
