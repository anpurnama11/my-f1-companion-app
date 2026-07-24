package com.anpurnama.f1_app.feature.round

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.RoundMode
import com.anpurnama.f1_app.f1.model.ScheduledSession
import com.anpurnama.f1_app.f1.model.SessionType
import com.anpurnama.f1_app.f1.model.toDeviceLocalLabel
import com.anpurnama.f1_app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundScreen(
    year: Int,
    round: Int,
    onCircuitClick: (circuitId: String) -> Unit,
    onSessionResultClick: (year: Int, round: Int, session: SessionType) -> Unit = { _, _, _ -> },
    viewModel: RoundViewModel = rememberRoundViewModel(year, round),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = (state as? RoundViewModel.UiState.Sections) ?: return
    val circuit = sections.race?.circuit
        ?: (sections.results as? SectionUiState.Content)?.data?.circuit
        ?: (sections.qualifying as? SectionUiState.Content)?.data?.circuit
    val headerName = sections.race?.name
        ?: (sections.results as? SectionUiState.Content)?.data?.raceName
        ?: (sections.qualifying as? SectionUiState.Content)?.data?.raceName
        ?: "Round $round"
    val mode = sections.mode ?: when {
        sections.race != null -> if (sections.race.winnerId == null) RoundMode.Upcoming else RoundMode.Past
        sections.results is SectionUiState.Content -> RoundMode.Past
        else -> RoundMode.Upcoming
    }

    PullToRefreshBox(
        isRefreshing = sections.results is SectionUiState.Loading ||
            sections.qualifying is SectionUiState.Loading || sections.season is SectionUiState.Loading,
        onRefresh = viewModel::refresh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.normal)
                .padding(top = Spacing.normal, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            RoundHeader(round, headerName, sections.race)
            if (circuit != null) {
                CircuitStatsCard(
                    circuit = circuit,
                    race = sections.race,
                    onClick = { onCircuitClick(circuit.id) },
                )
            }
            when (mode) {
                RoundMode.Upcoming -> UpcomingWeekend(
                    race = sections.race,
                    onSessionResultClick = onSessionResultClick,
                    year = year,
                    round = round,
                )
                RoundMode.Past -> PastResults(
                    race = sections.race,
                    onSessionResultClick = onSessionResultClick,
                    year = year,
                    round = round,
                )
            }
        }
    }
}

@Composable
private fun RoundHeader(round: Int, name: String, race: Race?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Round $round", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold)
        race?.circuit?.let { circuit ->
            Text(
                listOfNotNull(circuit.city, circuit.country).joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CircuitStatsCard(
    circuit: Circuit,
    race: Race?,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Circuit stats", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(circuit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Length", formatLength(circuit.circuitLengthRaw))
                    Stat("Laps", race?.laps?.toString() ?: "—")
                    Stat("Turns", circuit.corners?.toString() ?: "—")
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UpcomingWeekend(
    race: Race?,
    year: Int,
    round: Int,
    onSessionResultClick: (Int, Int, SessionType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Race weekend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val sessions = race?.schedule?.activeSessions().orEmpty()
        if (sessions.isEmpty()) {
            Text("Weekend schedule unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            sessions.forEach { WeekendSessionRow(it, showAction = false, year, round, onSessionResultClick) }
        }
    }
}

@Composable
private fun PastResults(
    race: Race?,
    year: Int,
    round: Int,
    onSessionResultClick: (Int, Int, SessionType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SecondaryTabRow(selectedTabIndex = 0) {
            Tab(selected = true, onClick = {}, text = { Text("Results") })
        }
        val sessions = race?.schedule?.activeSessions().orEmpty()
        if (sessions.isEmpty()) {
            Text("Session schedule unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            sessions.forEach { WeekendSessionRow(it, showAction = true, year, round, onSessionResultClick) }
        }
    }
}

@Composable
private fun WeekendSessionRow(
    session: ScheduledSession,
    showAction: Boolean,
    year: Int,
    round: Int,
    onSessionResultClick: (Int, Int, SessionType) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.normal),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(session.type.label, fontWeight = FontWeight.SemiBold)
                Text(session.slot.toDeviceLocalLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showAction) {
                Button(onClick = { onSessionResultClick(year, round, session.type) }) {
                    Text("Results")
                }
            }
        }
    }
}

private fun formatLength(raw: String): String = raw.filter(Char::isDigit).toIntOrNull()
    ?.let { "%.3f km".format(it / 1000.0) } ?: "—"

@Composable
private fun rememberRoundViewModel(year: Int, round: Int): RoundViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(
        factory = roundViewModelFactory(
            year = year,
            round = round,
            getRoundResults = wiring.getRoundResults,
            getRoundQualifying = wiring.getRoundQualifying,
            getSeason = wiring.getSeason,
        ),
    )
}
