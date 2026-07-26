package com.anpurnama.f1_app.feature.circuit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.CircuitDetail
import com.anpurnama.f1_app.f1.model.CircuitMostWins
import com.anpurnama.f1_app.f1.model.LapRecord
import com.anpurnama.f1_app.f1.model.MostWinningDriver
import com.anpurnama.f1_app.f1.model.MostWinningTeam
import com.anpurnama.f1_app.ui.theme.Circuits
import com.anpurnama.f1_app.ui.theme.Spacing
import com.anpurnama.f1_app.ui.theme.TeamColors

/**
 * Circuit detail page — the home for the circuit-scoped research stats.
 *
 * Two independently-failing sections per ADR 0002 (the same shared UX
 * family used on Homepage §1-§3 and Round detail):
 *
 *  - **Metadata** — f1api.dev `/circuits/{circuitId}`: length, corners,
 *    first-GP year, all-time lap record with attribution.
 *  - **Most wins** — jolpica aggregated over the circuit's race history:
 *    top driver and top team, each with their win count.
 *
 * **Top speed is intentionally absent from v1** (ticket 10 / ADR 0009
 * removed the OpenF1 dependency). The §3 nearest-GP card that this page
 * used to be opened from was also removed by ticket 10; the only
 * remaining entry point is the RoundDetail circuit block.
 *
 * Navigation: this screen is reached from
 * `Route.CircuitDetail(circuitId)`. The destination route is wired
 * in `NavShell`; no further nav edges are required here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitScreen(
    circuitId: String,
    viewModel: CircuitViewModel = rememberCircuitViewModel(circuitId),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = (state as? CircuitViewModel.UiState.Sections) ?: return
    val accent = Circuits.forId(circuitId).takeIf { it != Color.Unspecified }

    PullToRefreshBox(
        isRefreshing = sections.metadata is SectionUiState.Loading ||
            sections.mostWins is SectionUiState.Loading,
        onRefresh = viewModel::refresh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Spacing.normal)
                .padding(top = Spacing.normal, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            OutcomeContent(state = sections.metadata) { detail ->
                MetadataCard(detail, accent)
            }
            OutcomeContent(state = sections.mostWins) { mostWins ->
                MostWinsCard(mostWins, accent)
            }
        }
    }
}

// ─── metadata card ────────────────────────────────────────────────────────

@Composable
private fun MetadataCard(detail: CircuitDetail, accent: Color?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Circuit",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (accent != null && accent != Color.Unspecified) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .background(accent)
                            .align(Alignment.CenterVertically)
                            .size(width = 6.dp, height = 96.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(Spacing.normal),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = detail.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val place = listOfNotNull(detail.city, detail.country)
                        .joinToString(", ")
                        .ifBlank { null }
                    if (place != null) {
                        Text(
                            text = place,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Stat("Length", "%.3f km".format(detail.circuitLengthKm))
                        Stat("Corners", detail.numberOfCorners?.toString() ?: "—")
                        Stat(
                            "First GP",
                            detail.firstParticipationYear?.toString() ?: "—",
                        )
                    }
                    detail.lapRecord?.let { record ->
                        LapRecordRow(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LapRecordRow(record: LapRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = "Lap record",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatLapRecordTime(record.time),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${record.year} · ${record.driverId} · ${record.teamId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * f1api.dev's lap-record wire format is `MM:SS:mmm` (a 3-tuple with
 * milliseconds), e.g. `"1:21:046"`. Drop the trailing 3-digit
 * millisecond block to render as `M:SS.SSS` so the duration is
 * legible without losing the per-millisecond precision. Lap records
 * over 1 minute are routine (Spa 1:46, Bahrain 1:31, etc.) so the
 * minute is always present.
 */
private fun formatLapRecordTime(wire: String): String {
    val parts = wire.split(":")
    if (parts.size != 3) return wire
    val (m, s, ms) = parts
    return "$m:$s.$ms"
}

// ─── most-wins card ───────────────────────────────────────────────────────

@Composable
private fun MostWinsCard(mostWins: CircuitMostWins, accent: Color?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "Most wins",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.normal),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                MostWinsRow(
                    label = "Top team",
                    primary = mostWins.topTeam?.name ?: "—",
                    detail = mostWins.topTeam?.let { "${it.wins} wins" },
                    teamId = mostWins.topTeam?.teamId,
                )
                MostWinsRow(
                    label = "Top driver",
                    primary = mostWins.topDriver?.name ?: "—",
                    detail = mostWins.topDriver?.let { "${it.wins} wins" },
                    teamId = mostWins.topDriver?.driverId,
                )
                if (mostWins.totalRaces > 0) {
                    Text(
                        text = "Across ${mostWins.totalRaces} races",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MostWinsRow(
    label: String,
    primary: String,
    detail: String?,
    teamId: String?,
) {
    val rowAccent = teamId
        ?.let { TeamColors.forId(it) }
        ?.takeIf { it != Color.Unspecified }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rowAccent != null) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .background(rowAccent)
                    .size(width = 6.dp, height = 40.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (rowAccent != null) Spacing.normal else 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── VM factory ───────────────────────────────────────────────────────────

@Composable
private fun rememberCircuitViewModel(circuitId: String): CircuitViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(
        factory = circuitViewModelFactory(
            circuitId = circuitId,
            getCircuit = wiring.getCircuit,
            getCircuitMostWins = wiring.getCircuitMostWins,
        ),
    )
}
