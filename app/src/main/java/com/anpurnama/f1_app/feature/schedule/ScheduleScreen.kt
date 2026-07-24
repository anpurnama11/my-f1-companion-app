package com.anpurnama.f1_app.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.RoundPodium
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.ui.theme.Spacing

/**
 * Schedule tab — **tab switcher** shape (revision 1 of ticket 03).
 * Two tabs at the top: **Upcoming** and **Past**. The visible page's
 * list is rendered by a horizontal pager; switching pages is instant
 * because the data is held in the VM (`podiums`).
 *
 *  - **Upcoming tab** — round, GP name, race date, city.
 *  - **Past tab** — same fields + podium winner cell (P1/P2/P3, with
 *    a retry row on per-row failure). No countdown.
 *
 * Per the shared UX family: a past-row podium failure degrades to a
 * retry row and never blanks the rest of the schedule. Tapping any
 * row (upcoming or past) invokes [onRoundClick] which the NavShell
 * maps to `backStack.add(Route.RoundDetail(year, round))`.
 *
 * Pull-to-refresh on either tab re-fetches the season + every past
 * podium. The VM owns all per-row loads;
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

    val pagerState = rememberPagerState(pageCount = { ScheduleTab.entries.size })

    // Season is the single loading signal for the pull affordance.
    // Per-row pods / images each have their own state; refreshing
    // them is the row-level retry button, not the swipe.
    val isRefreshing = sections.season is SectionUiState.Loading
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
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
                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    ScheduleTab.entries.forEach { tab ->
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
                        .testTag("schedule-tab-pager"),
                ) { page ->
                    OutcomeContent(state = sections.season) { season ->
                        // The pager composes the visible page and may prefetch
                        // its neighbor. Both lists are held by the VM, so
                        // swiping reads existing data without re-fetching.
                        when (ScheduleTab.entries[page]) {
                            ScheduleTab.Upcoming -> UpcomingList(
                                season = season,
                                year = sections.year,
                                onRoundClick = onRoundClick,
                            )
                            ScheduleTab.Past -> PastList(
                                season = season,
                                year = sections.year,
                                podiums = sections.podiums,
                                onRoundClick = onRoundClick,
                                onRetryPodium = {
                                    if (!viewModel.retryPodium(it)) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Schedule is still loading")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.normal),
        )
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
    onRoundClick: (year: Int, round: Int) -> Unit,
    onRetryPodium: (round: Int) -> Unit,
) {
    // Most-recent first — the Past tab is a "what just happened" scan
    // path. Round number is monotonic within a season, so descending
    // round ≡ reverse-chronological. (Upcoming keeps ascending: next
    // race first.)
    val past = season.races.filter { it.winnerId != null }
        .sortedByDescending { it.round }
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
    showPodium: Boolean,
    podium: SectionUiState<RoundPodium>?,
    onClick: () -> Unit,
    onRetryPodium: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Open Round ${race.round}, ${race.circuit.name}"
            },
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
                        text = race.name.ifBlank { race.circuit.name },
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
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
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
        is SectionUiState.Content -> InlinePodium(
            drivers = podium.data.topThree.map { it.driverShortName },
        )
    }
}

/**
 * Past-row podium line. Single inline text in the row's existing visual
 * language — no container, no background, no chip shape.
 *
 * ```
 * P1 RUS  ·  P2 ANT  ·  P3 LEC
 * ```
 *
 * Position label and driver code share `titleMedium` SemiBold (matches
 * the GP-name weight above). Position is `onSurfaceVariant` (muted);
 * driver code is `onSurface` (full). Color carries the label-vs-value
 * hierarchy so the line reads as one typeface, not two mismatched
 * sizes. P1 dominance is implicit — left-to-right scan reads P1 first.
 *
 * Replaces the v1 chips (`/impeccable bolder` overshot, `/quieter`
 * still read as a foreign primitive in the row; `/impeccable shape`
 * resolved the premise itself).
 */
@Composable
private fun InlinePodium(drivers: List<String?>) {
    Row(verticalAlignment = Alignment.Bottom) {
        drivers.forEachIndexed { index, code ->
            Text(
                text = "P${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            // `start` padding on the code Text — without it the position
            // and code mash into one word (`P1ANT`); the brief was `P1
            // RUS` with breathing room. Spacing.xs matches the breathing
            // room on each side of the middle dot, so the line has one
            // consistent rhythm.
            Text(
                text = code ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.xs),
            )
            if (index < drivers.lastIndex) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }
}

/**
 * Renders the race day as `"Sat 2 Mar"`, e.g. `2024-03-02` → `"Sat 2 Mar"`.
 * Date only — the race time lives on the Homepage countdown card, so the
 * Schedule row keeps to a single glanceable line. Parses f1api.dev's
 * raw `YYYY-MM-DD` string; returns `null` on missing or malformed input,
 * which the caller already drops from the layout.
 */
private fun formatRaceDate(race: Race): String? {
    val date = race.schedule?.race?.date ?: return null
    return runCatching {
        val parts = date.split('-')
        val ld = LocalDate(
            year = parts[0].toInt(),
            monthNumber = parts[1].toInt(),
            dayOfMonth = parts[2].toInt(),
        )
        val dow = ld.dayOfWeek.name.take(3)
        val mon = ld.month.name.take(3)
            .lowercase()
            .replaceFirstChar(Char::uppercase)
        "$dow ${ld.dayOfMonth} $mon"
    }.getOrNull()
}

@Composable
private fun rememberScheduleViewModel(): ScheduleViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(
        factory = scheduleViewModelFactory(
            getSeason = wiring.getSeason,
            getRoundPodium = wiring.getRoundPodium,
        )
    )
}
