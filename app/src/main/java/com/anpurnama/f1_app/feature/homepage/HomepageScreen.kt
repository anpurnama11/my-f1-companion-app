package com.anpurnama.f1_app.feature.homepage

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
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
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.SessionTime
import com.anpurnama.f1_app.f1.model.TopSpeed
import com.anpurnama.f1_app.f1.model.WeekendSchedule
import com.anpurnama.f1_app.ui.theme.Circuits
import com.anpurnama.f1_app.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val RACE_DURATION = 3.hours

/**
 * Homepage — three sections, each section fails independently.
 *
 *  - §1 Favorite pager (HorizontalPager of driver cards + team card + GP card)
 *  - §2 Season progress aggregates (carried over from ticket 01)
 *  - §3 Nearest GP card with top speed
 *
 * The screen reads the VM's `UiState.Sections` and renders each section
 * via the shared `OutcomeContent` family — Loading/Failure/Success, each
 * independent. A failure on one section never blanks the others.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomepageScreen(
    onCircuitClick: (String) -> Unit = {},
    viewModel: HomepageViewModel = rememberHomepageViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = (state as? HomepageViewModel.UiState.Sections)
        ?: return  // initialValue placeholder, replaced quickly

    // Track which sections are loading for the pull-to-refresh affordance.
    // `favorites` stays Success once the DataStore Flow emits, so it's
    // never in Loading past first read — excluded by design.
    val anyLoading = sections.season is SectionUiState.Loading ||
        sections.nextRace is SectionUiState.Loading ||
        sections.drivers is SectionUiState.Loading ||
        sections.constructors is SectionUiState.Loading ||
        sections.topSpeed is SectionUiState.Loading ||
        sections.weekendSchedule is SectionUiState.Loading ||
        sections.circuitImage is SectionUiState.Loading

    PullToRefreshBox(
        isRefreshing = anyLoading,
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
            Section1FavoritesPager(
                favorites = sections.favorites,
                drivers = sections.drivers,
                constructors = sections.constructors,
                nextRace = sections.nextRace,
                weekendSchedule = sections.weekendSchedule,
                circuitImage = sections.circuitImage,
            )
            Section2Season(sections.season)
            Section3NearestGp(
                nextRace = sections.nextRace,
                topSpeed = sections.topSpeed,
                onClickCircuit = { circuitId ->
                    onCircuitClick(circuitId)
                },
            )
        }
    }
}

// ─── §1 Favorite pager ────────────────────────────────────────────────────

@Composable
private fun Section1FavoritesPager(
    favorites: SectionUiState<Favorites>,
    drivers: SectionUiState<List<DriverStanding>>,
    constructors: SectionUiState<List<ConstructorStanding>>,
    nextRace: SectionUiState<NextRace?>,
    weekendSchedule: SectionUiState<WeekendSchedule?>,
    circuitImage: SectionUiState<String?>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Pager renders the resolved cards if everything loaded, or the
        // loading state if any upstream is still resolving.
        val cards = buildList {
            val favs = (favorites as? SectionUiState.Content)?.data
            val drv = (drivers as? SectionUiState.Content)?.data.orEmpty()
            val con = (constructors as? SectionUiState.Content)?.data.orEmpty()
            val ws = (weekendSchedule as? SectionUiState.Content)?.data
            val next = (nextRace as? SectionUiState.Content)?.data
            if (favs != null) {
                favs.driver1Id?.let { id -> drv.firstOrNull { it.driverId == id } }?.let { add(PagerCard.Driver(it)) }
                favs.driver2Id?.let { id -> drv.firstOrNull { it.driverId == id } }?.let { add(PagerCard.Driver(it)) }
                favs.teamId?.let { id -> con.firstOrNull { it.teamId == id } }?.let { add(PagerCard.Team(it)) }
            }
            // Countdown card needs the next race (for circuit name + accent)
            // and the weekend schedule (for the next session + time). Both
            // resolved = countdown. Only one resolved = show the available
            // half (race name without countdown, or "Loading\u2026" otherwise).
            val imageUrl = (circuitImage as? SectionUiState.Content)?.data
            if (next != null) add(PagerCard.Countdown(next, ws, imageUrl))
        }
        if (cards.isEmpty()) {
            // Initial load (or all upstreams failed) — render the first
            // failure surfaced, or Loading if everything is still in flight.
            val failureState = when {
                favorites is SectionUiState.Error -> favorites
                drivers is SectionUiState.Error -> drivers
                constructors is SectionUiState.Error -> constructors
                nextRace is SectionUiState.Error -> nextRace
                weekendSchedule is SectionUiState.Error -> weekendSchedule
                circuitImage is SectionUiState.Error -> circuitImage
                else -> SectionUiState.Loading
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                OutcomeContent(state = failureState) {
                    /* nothing — the section just stays empty if no picks */
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { cards.size })
            HorizontalPager(
                state = pagerState,
                pageSpacing = Spacing.md,
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            ) { page ->
                when (val card = cards[page]) {
                    is PagerCard.Driver -> DriverCard(card.standing)
                    is PagerCard.Team -> TeamCard(card.standing)
                    is PagerCard.Countdown -> CountdownCard(card.race, card.schedule, card.circuitImageUrl)
                }
            }
            // Page-indicator dots (small row of circles).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(cards.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            ),
                    )
                }
            }
        }
    }
}

private sealed interface PagerCard {
    data class Driver(val standing: DriverStanding) : PagerCard
    data class Team(val standing: ConstructorStanding) : PagerCard
    // Pager card for §1's countdown. The `NextRace` carries the circuit
    // (id + name for the accent strip) and the schedule carries the next
    // upcoming session + its start instant. Either may be missing.
    // `circuitImageUrl` is the decorative OpenF1 track-layout image.
    data class Countdown(
        val race: NextRace,
        val schedule: WeekendSchedule?,
        val circuitImageUrl: String?,
    ) : PagerCard
}

@Composable
private fun DriverCard(standing: DriverStanding) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.normal),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Driver",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    text = standing.driverName.ifEmpty { standing.driverId },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (standing.driverShortName != null) {
                    Text(
                        text = "${standing.driverShortName} · #${standing.driverNumber ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "P${standing.position} · ${standing.points} pts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TeamCard(standing: ConstructorStanding) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.normal),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Constructor",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    text = standing.teamName.ifEmpty { standing.teamId },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (standing.country != null) {
                    Text(
                        text = standing.country,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "P${standing.position} · ${standing.points} pts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountdownCard(
    race: NextRace,
    schedule: WeekendSchedule?,
    circuitImageUrl: String?,
) {
    // Tick `now` every 30s so a card the user is reading updates without
    // waiting for the next state push. `LaunchedEffect(Unit)` keys on
    // composition entry — the timer restarts on navigate-away/return,
    // which is fine: the first tick is `Clock.System.now()` immediately.
    var nowMillis by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30.seconds)
            nowMillis = Clock.System.now().toEpochMilliseconds()
        }
    }
    val now = Instant.fromEpochMilliseconds(nowMillis)

    // Pick the "next upcoming" session; if none, treat the race as
    // LIVE only until the race window has passed. Once the race is
    // over we show a transient "RACE COMPLETE" state instead of
    // staying stuck on "LIVE" forever.
    val raceSession = schedule?.sessions?.lastOrNull { it.shortLabel == "RACE" }

    val next: SessionTime? = schedule?.nextUpcoming(now)
    val live: SessionTime? = next ?: raceSession?.takeIf {
        now < it.start.plus(RACE_DURATION)
    }
    val raceComplete = raceSession?.let {
        now >= it.start.plus(RACE_DURATION)
    } ?: false

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.normal),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Header row: label + session chip.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Next event",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (next != null) {
                            SessionChip(text = next.shortLabel)
                        } else if (live != null) {
                            SessionChip(text = "LIVE", emphasis = true)
                        } else if (raceComplete) {
                            SessionChip(text = "RACE COMPLETE")
                        }
                    }
                    // Big countdown (or fallback labels).
                    Text(
                        text = when {
                            next != null -> countdownTo(next.start, now)
                            live != null -> "LIVE"
                            raceComplete -> "RACE COMPLETE"
                            schedule == null -> "…"  // still loading
                            else -> "—"              // no sessions at all
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Footer: session name + date/time + GP context.
                    Column {
                        val session = next ?: live
                        val sessionLine = session?.let { "${it.label} · ${formatStart(it.start)}" }
                        val raceLine = "${race.circuit.country} · Round ${race.round}"
                        Text(
                            text = sessionLine ?: raceLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (sessionLine != null) {
                            Text(
                                text = raceLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (circuitImageUrl != null) {
                    AsyncImage(
                        model = circuitImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = Spacing.sm)
                            .width(140.dp)
                            .height(120.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(
                            Circuits.forId(race.circuit.id),
                            BlendMode.SrcIn,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionChip(text: String, emphasis: Boolean = false) {
    val container = if (emphasis) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (emphasis) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

private fun countdownTo(start: Instant, now: Instant): String {
    val ms = start.toEpochMilliseconds() - now.toEpochMilliseconds()
    if (ms <= 0) return "LIVE"
    val totalMinutes = ms / 60_000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/**
 * Formats a session start as "EEE HH:mm" in the device's local timezone.
 * Uses `kotlinx.datetime.toLocalDateTime` for the conversion; the day
 * name is the enum's `name` first-3-chars (e.g. "MONDAY" -> "MON").
 *
 * ponytail: java.time's `DateTimeFormatter` pattern support would be
 * one line, but it's API 26+ and the project's minSdk is 24 without
 * core-library desugaring. Three lines of manual `take(3)` + `%02d`
 * is cheaper than flipping the desugar switch for one screen.
 */
private fun formatStart(start: Instant): String {
    val ldt = start.toLocalDateTime(TimeZone.currentSystemDefault())
    val dow = ldt.dayOfWeek.name.take(3)
    return "$dow ${"%02d".format(ldt.hour)}:${"%02d".format(ldt.minute)}"
}

// ─── §2 Season progress ──────────────────────────────────────────────────

@Composable
private fun Section2Season(season: SectionUiState<Season>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutcomeContent(state = season) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "Season ${s.year}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ProgressCard(percent = (s.progressPercent * 100).toInt())
                StatCard(label = "GPs completed", value = s.completedGp.toString())
                StatCard(label = "Total km covered", value = "%.1f".format(s.totalKm))
                StatCard(label = "Total laps", value = s.totalLaps.toString())
            }
        }
    }
}

@Composable
private fun ProgressCard(percent: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.normal)) {
            Text(
                text = "Progress",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.normal)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─── §3 Nearest GP ────────────────────────────────────────────────────────

@Composable
private fun Section3NearestGp(
    nextRace: SectionUiState<NextRace?>,
    topSpeed: SectionUiState<TopSpeed?>,
    onClickCircuit: (circuitId: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutcomeContent(state = nextRace) { race ->
            if (race == null) {
                // Off-season.
                Text(
                    text = "No upcoming race",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CircuitCard(
                    race = race,
                    topSpeed = topSpeed,
                    onClick = { onClickCircuit(race.circuit.id) },
                )
            }
        }
    }
}

@Composable
private fun CircuitCard(
    race: NextRace,
    topSpeed: SectionUiState<TopSpeed?>,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.normal),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Circuit brand-accent strip (small bar, full-bleed at top).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Circuits.forId(race.circuit.id)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Round ${race.round}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = race.circuit.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (race.raceDate != null) {
                        Text(
                            text = "${race.raceDate} · ${race.laps ?: "—"} laps · ${race.circuit.corners ?: "—"} corners",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Top speed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (val ts = topSpeed) {
                        is SectionUiState.Content -> {
                            if (ts.data != null) {
                                Text(
                                    text = "${ts.data.speedKph} km/h",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                // Spacer only — pre-2023 round or no Qualifying
                                // session on the OpenF1 calendar. Not a value
                                // placeholder; "no data" is the truth and the
                                // cell stays empty (per the ticket 02 spec).
                                Box(modifier = Modifier.height(28.dp))
                            }
                        }
                        is SectionUiState.Error -> {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is SectionUiState.Loading -> {
                            Text(
                                text = "…",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── helpers ──────────────────────────────────────────────────────────────

/**
 * Composes the [HomepageViewModel] from `Wiring` once, so the screen
 * default param doesn't repeat the `(applicationContext as F1App).wiring`
 * cast six times. Each section of the VM needs a different use case
 * (and the cache for the favorites + seed) — all live on `Wiring`.
 */
@Composable
private fun rememberHomepageViewModel(): HomepageViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(
        factory = homepageViewModelFactory(
            getSeason = wiring.getSeason,
            getNextRace = wiring.getNextRace,
            getRaceWeekendSchedule = wiring.getRaceWeekendSchedule,
            getDriversStandings = wiring.getDriversStandings,
            getConstructorsStandings = wiring.getConstructorsStandings,
            getCircuitTopSpeed = wiring.getCircuitTopSpeed,
            getCircuitImage = wiring.getCircuitImage,
            favoritesCache = wiring.favoritesCache,
        )
    )
}
