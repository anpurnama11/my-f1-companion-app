package com.anpurnama.f1_app.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.RoundPodium
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.ui.theme.Circuits
import com.anpurnama.f1_app.ui.theme.Spacing

/**
 * Schedule tab — **tab switcher** shape (revision 1 of ticket 03).
 * Two tabs at the top: **Upcoming** and **Past**. The active tab's
 * list is the only one in composition; switching tabs is instant
 * because the data is held in the VM (`podiums`, `circuitImages`).
 *
 *  - **Upcoming tab** — round, GP name, race date, city, circuit
 *    image (OpenF1 track layout, brand accent fallback if the fetch
 *    fails or there's no country for OpenF1 join).
 *  - **Past tab** — same fields + podium winner cell (P1/P2/P3, with
 *    a retry row on per-row failure) + circuit image from OpenF1.
 *    No countdown.
 *
 * Per the shared UX family: a past-row podium failure degrades to a
 * retry row and never blanks the rest of the schedule. Tapping any
 * row (upcoming or past) invokes [onRoundClick] which the NavShell
 * maps to `backStack.add(Route.RoundDetail(year, round))`.
 *
 * Pull-to-refresh on either tab re-fetches the season + every past
 * podium + every past circuit image. The VM owns all per-row loads;
 * the screen has no per-row `LaunchedEffect`. This is by design:
 * `LaunchedEffect(race.round)` would not re-fire on same-key
 * re-render after a refresh, so the VM must be the one to re-fire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onRoundClick: (year: Int, round: Int) -> Unit,
    viewModel: ScheduleViewModel = rememberScheduleViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = (state as? ScheduleViewModel.UiState.Sections) ?: return

    var activeTab by rememberSaveable { mutableStateOf(ScheduleTab.Upcoming) }

    // Season is the single loading signal for the pull affordance.
    // Per-row pods / images each have their own state; refreshing
    // them is the row-level retry button, not the swipe.
    val isRefreshing = sections.season is SectionUiState.Loading

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.normal)
                .padding(top = Spacing.normal),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            TabRow(
                selectedTabIndex = activeTab.ordinal,
            ) {
                ScheduleTab.entries.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = { Text(tab.label) },
                    )
                }
            }

            OutcomeContent(state = sections.season) { season ->
                // `when` on the active tab — only the active tab's
                // list is composed. The VM holds the data for both
                // surfaces (podiums + circuitImages are pre-loaded
                // eagerly in `loadSeason`'s Content branch), so a
                // tab switch re-composes the new list and reads the
                // existing data — no re-fetch.
                when (activeTab) {
                    ScheduleTab.Upcoming -> UpcomingList(
                        season = season,
                        year = sections.year,
                        circuitImages = sections.circuitImages,
                        onRoundClick = onRoundClick,
                    )
                    ScheduleTab.Past -> PastList(
                        season = season,
                        year = sections.year,
                        podiums = sections.podiums,
                        circuitImages = sections.circuitImages,
                        onRoundClick = onRoundClick,
                        onRetryPodium = { viewModel.retryPodium(it) },
                    )
                }
            }
        }
    }
}

/** Local enum drives the TabRow + the `when` branches. */
private enum class ScheduleTab(val label: String) {
    Upcoming("Upcoming"),
    Past("Past"),
}

@Composable
private fun UpcomingList(
    season: Season,
    year: Int,
    circuitImages: Map<Int, SectionUiState<String?>>,
    onRoundClick: (year: Int, round: Int) -> Unit,
) {
    val upcoming = season.races.filter { it.winnerId == null }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (upcoming.isEmpty()) {
            EmptyState("No upcoming rounds")
        } else {
            upcoming.forEach { race ->
                ScheduleRow(
                    race = race,
                    circuitImage = circuitImages[race.round],
                    showPodium = false,
                    podium = null,
                    onClick = { onRoundClick(year, race.round) },
                    onRetryPodium = {},
                )
            }
        }
    }
}

@Composable
private fun PastList(
    season: Season,
    year: Int,
    podiums: Map<Int, SectionUiState<RoundPodium>>,
    circuitImages: Map<Int, SectionUiState<String?>>,
    onRoundClick: (year: Int, round: Int) -> Unit,
    onRetryPodium: (round: Int) -> Unit,
) {
    val past = season.races.filter { it.winnerId != null }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (past.isEmpty()) {
            EmptyState("No past rounds")
        } else {
            past.forEach { race ->
                // Per-row podium data is fired eagerly by the VM in
                // `loadSeason`'s Content branch. The screen does NOT
                // have a `LaunchedEffect(race.round)` here — that
                // was the revision-0 pattern, removed in revision 1
                // because the screen's same-key re-render would not
                // re-fire it on refresh, making it the wrong place
                // to own re-fetch. The VM owns the full lifecycle.
                ScheduleRow(
                    race = race,
                    circuitImage = circuitImages[race.round],
                    showPodium = true,
                    podium = podiums[race.round],
                    onClick = { onRoundClick(year, race.round) },
                    onRetryPodium = { onRetryPodium(race.round) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Shared row layout for both tabs. Upcoming passes `showPodium=false`
 * and `podium=null`; Past passes `showPodium=true` + the per-round
 * podium state. Layout (revision 1):
 *  - Header: round number + GP name
 *  - City / country line
 *  - Race date (race slot only — the v1 5-session breakdown is gone)
 *  - Circuit image (right column, decorative; OpenF1 with brand
 *    accent as fallback)
 *  - Podium cell (Past only)
 */
@Composable
private fun ScheduleRow(
    race: Race,
    circuitImage: SectionUiState<String?>?,
    showPodium: Boolean,
    podium: SectionUiState<RoundPodium>?,
    onClick: () -> Unit,
    onRetryPodium: () -> Unit,
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Round ${race.round}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = race.name.ifEmpty { race.circuit.name },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val cityCountry = listOfNotNull(race.circuit.city, race.circuit.country)
                        .joinToString(", ")
                    if (cityCountry.isNotEmpty()) {
                        Text(
                            text = cityCountry,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val raceDate = formatRaceDate(race)
                    if (raceDate != null) {
                        Text(
                            text = raceDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CircuitImage(
                    circuit = race.circuit,
                    image = circuitImage,
                )
            }
            if (showPodium) {
                PodiumCell(
                    podium = podium,
                    onRetryPodium = onRetryPodium,
                )
            }
        }
    }
}

/**
 * Decorative circuit image. The accent strip is always rendered
 * (18% alpha) so the cell has identity even while the image loads
 * and when OpenF1 returns no image. When an image URL is resolved
 * it tints on top of the accent, matching the homepage §3 card.
 */
@Composable
private fun CircuitImage(
    circuit: Circuit,
    image: SectionUiState<String?>?,
) {
    val accent = Circuits.forId(circuit.id)
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.18f)),
    ) {
        val url = (image as? SectionUiState.Content)?.data
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(accent, BlendMode.SrcIn),
            )
        }
    }
}

@Composable
private fun PodiumCell(
    podium: SectionUiState<RoundPodium>?,
    onRetryPodium: () -> Unit,
) {
    when (podium) {
        null, is SectionUiState.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        }
        is SectionUiState.Error -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = podium.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetryPodium) {
                Text("Retry")
            }
        }
        is SectionUiState.Content -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            podium.data.topThree.forEachIndexed { index, r ->
                PodiumChip(position = index + 1, name = r.driverShortName, team = r.teamName)
            }
        }
    }
}

@Composable
private fun PodiumChip(position: Int, name: String?, team: String?) {
    val label = name ?: "—"
    val teamLabel = team ?: ""
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.sm, vertical = 4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "P$position",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (teamLabel.isNotEmpty()) {
            Text(
                text = teamLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // ponytail: no separator between chips — the P1/P2/P3 labels are
    // the visual break. If designers want pill chips later, wrap in a
    // Box(.background(surfaceContainerHighest)) and add `Spacer`.
    Spacer(Modifier.width(Spacing.xs))
}

/** "Sun 23 Mar · 15:00" — date + time as carried on the race session. */
private fun formatRaceDate(race: Race): String? {
    val slot = race.schedule?.race ?: return null
    val date = slot.date
    val time = slot.time
    return when {
        date != null && time != null -> "$date · $time"
        date != null -> date
        time != null -> time
        else -> null
    }
}

@Composable
private fun rememberScheduleViewModel(): ScheduleViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(
        factory = scheduleViewModelFactory(
            getSeason = wiring.getSeason,
            getRoundPodium = wiring.getRoundPodium,
            getCircuitImage = wiring.getCircuitImage,
        )
    )
}
