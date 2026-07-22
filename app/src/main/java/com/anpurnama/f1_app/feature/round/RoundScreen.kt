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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.QualifyingResult
import com.anpurnama.f1_app.f1.model.RoundQualifying
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.RoundResults
import com.anpurnama.f1_app.ui.theme.Spacing

/**
 * Round detail page — three independently-failing blocks:
 *  1. Race results (`/race`)
 *  2. Qualifying results (`/qualy`)
 *  3. Circuit (name + length, clickable → `onCircuitClick(circuit.id)`)
 *
 *  Each block renders via the shared [OutcomeContent] family. A
 *  failure on one never blanks the others (section independence
 *  inherited from ticket 01 / 02). Pull-to-refresh re-fires both
 *  use cases with `forceRefresh = true`.
 *
 *  `year` and `round` are constructor params carried by the VM
 *  instance; the screen receives them from the `Route.RoundDetail`
 *  nav key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundScreen(
    year: Int,
    round: Int,
    onCircuitClick: (circuitId: String) -> Unit,
    viewModel: RoundViewModel = rememberRoundViewModel(year, round),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = (state as? RoundViewModel.UiState.Sections) ?: return

    // Tie the pull-to-refresh spinner to the live loaders; both
    // sections flip to Loading when refresh() fires, so either is a
    // honest signal.
    val isRefreshing = sections.results is SectionUiState.Loading ||
        sections.qualifying is SectionUiState.Loading

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.normal)
                .padding(top = Spacing.normal, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            RaceResultsBlock(state = sections.results)
            QualifyingBlock(state = sections.qualifying)
            // Circuit block — appears under either results block
            // (first one that's Content) so the screen still renders
            // a circuit when results fail. The circuit copy uses the
            // race envelope (date / raceName) and the qualy envelope
            // carries the same `Circuit` field; if both fail, the
            // circuit block itself stays absent.
            val circuit = circuitFromFirstContent(sections)
            if (circuit != null) {
                val header = headerFromFirstContent(sections)
                CircuitBlock(
                    header = header,
                    circuit = circuit,
                    onClick = { onCircuitClick(circuit.id) },
                )
            }
        }
    }
}

private fun circuitFromFirstContent(
    state: RoundViewModel.UiState.Sections,
): Circuit? {
    val fromResults = (state.results as? SectionUiState.Content)?.data?.circuit
    if (fromResults != null) return fromResults
    return (state.qualifying as? SectionUiState.Content)?.data?.circuit
}

private data class RoundHeader(
    val raceName: String,
    val round: Int,
    val date: String?,
    val time: String?,
)

private fun headerFromFirstContent(
    state: RoundViewModel.UiState.Sections,
): RoundHeader? {
    val r = (state.results as? SectionUiState.Content)?.data
    if (r != null) return RoundHeader(r.raceName, r.round, r.date, r.time)
    val q = (state.qualifying as? SectionUiState.Content)?.data
    if (q != null) return RoundHeader(q.raceName, q.round, q.qualyDate, q.qualyTime)
    return null
}

@Composable
private fun PageHeader(header: RoundHeader) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = "Round ${header.round}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = header.raceName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val sub = listOfNotNull(
            header.date,
            header.time,
        ).joinToString(" · ")
        if (sub.isNotEmpty()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RaceResultsBlock(state: SectionUiState<RoundResults>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Race results",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutcomeContent(state = state, onRetry = null) { results ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                results.results.forEach { r -> ResultRow(r) }
            }
        }
    }
}

@Composable
private fun QualifyingBlock(state: SectionUiState<RoundQualifying>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Qualifying",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutcomeContent(state = state, onRetry = null) { qualy ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                qualy.results.forEach { r -> QualyRow(r) }
            }
        }
    }
}

@Composable
private fun ResultRow(r: RoundResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.normal),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (r.position == "NC") "NC" else "P${r.position}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${r.points} pts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = r.driverName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = r.teamName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Grid ${r.grid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = r.time ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun QualyRow(r: QualifyingResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.normal),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "P${r.gridPosition}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = r.driverName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = r.teamName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                QSegment("Q1", r.q1)
                QSegment("Q2", r.q2)
                QSegment("Q3", r.q3)
            }
        }
    }
}

@Composable
private fun QSegment(label: String, time: String?) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = time ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CircuitBlock(
    header: RoundHeader?,
    circuit: Circuit,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Circuit",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // The page header is rendered by whichever results block
        // is Content; re-render here too so the circuit block is
        // self-contained if the caller navigates straight to it
        // (e.g. deep link). Cheap; the composable is small.
        if (header != null) PageHeader(header)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.normal),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = circuit.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val length = if (circuit.circuitLengthRaw.isNotEmpty()) circuit.circuitLengthRaw else null
                val corners = circuit.corners?.let { "$it corners" }
                val sub = listOfNotNull(
                    length,
                    corners,
                    listOfNotNull(circuit.city, circuit.country).joinToString(", ").ifEmpty { null },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberRoundViewModel(year: Int, round: Int): RoundViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(
        factory = roundViewModelFactory(
            year = year,
            round = round,
            getRoundResults = wiring.getRoundResults,
            getRoundQualifying = wiring.getRoundQualifying,
        )
    )
}
