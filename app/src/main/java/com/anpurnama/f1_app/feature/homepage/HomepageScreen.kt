package com.anpurnama.f1_app.feature.homepage

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.navigation.Route
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.TopSpeed
import com.anpurnama.f1_app.ui.theme.Circuits
import com.anpurnama.f1_app.ui.theme.Spacing
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

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
    backStack: NavBackStack<NavKey>? = null,
    viewModel: HomepageViewModel = rememberHomepageViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = (state as? HomepageViewModel.UiState.Sections)
        ?: return  // initialValue placeholder, replaced quickly

    // Track which sections are loading for the pull-to-refresh affordance.
    // `favorites` stays Success once the DataStore Flow emits, so it's
    // never in Loading past first read — excluded by design.
    val anyLoading = sections.season is Outcome.Loading ||
        sections.nextRace is Outcome.Loading ||
        sections.drivers is Outcome.Loading ||
        sections.constructors is Outcome.Loading ||
        sections.topSpeed is Outcome.Loading

    PullToRefreshBox(
        isRefreshing = anyLoading,
        onRefresh = { viewModel.refresh() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.normal)
                .padding(top = Spacing.normal),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Section1FavoritesPager(
                favorites = sections.favorites,
                drivers = sections.drivers,
                constructors = sections.constructors,
                nextRace = sections.nextRace,
            )
            Section2Season(sections.season)
            Section3NearestGp(
                nextRace = sections.nextRace,
                topSpeed = sections.topSpeed,
                onClickCircuit = { circuitId ->
                    backStack?.add(Route.CircuitDetail(circuitId))
                },
            )
        }
    }
}

// ─── §1 Favorite pager ────────────────────────────────────────────────────

@Composable
private fun Section1FavoritesPager(
    favorites: Outcome<Favorites>,
    drivers: Outcome<List<DriverStanding>>,
    constructors: Outcome<List<ConstructorStanding>>,
    nextRace: Outcome<NextRace?>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel("Pinned")
        // Pager renders the resolved cards if everything loaded, or the
        // loading state if any upstream is still resolving.
        val cards = buildList {
            val favs = (favorites as? Outcome.Success)?.data
            val drv = (drivers as? Outcome.Success)?.data.orEmpty()
            val con = (constructors as? Outcome.Success)?.data.orEmpty()
            val next = (nextRace as? Outcome.Success)?.data
            if (favs != null) {
                favs.driver1Id?.let { id -> drv.firstOrNull { it.driverId == id } }?.let { add(PagerCard.Driver(it)) }
                favs.driver2Id?.let { id -> drv.firstOrNull { it.driverId == id } }?.let { add(PagerCard.Driver(it)) }
                favs.teamId?.let { id -> con.firstOrNull { it.teamId == id } }?.let { add(PagerCard.Team(it)) }
            }
            next?.let { add(PagerCard.Race(it)) }
        }
        if (cards.isEmpty()) {
            // Initial load (or all upstreams failed) — render the first
            // failure surfaced, or Loading if everything is still in flight.
            val failureOutcome = when {
                favorites is Outcome.Failure -> favorites
                drivers is Outcome.Failure -> drivers
                constructors is Outcome.Failure -> constructors
                else -> Outcome.Loading
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                OutcomeContent(outcome = failureOutcome) {
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
                    is PagerCard.Race -> NextRaceCard(card.race)
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
    // Named `Race` to avoid shadowing the imported `NextRace` model
    // (which would otherwise create a recursive type in the field below).
    data class Race(val race: NextRace) : PagerCard
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
private fun NextRaceCard(race: NextRace) {
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
                text = "Next race",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    text = race.raceName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Round ${race.round} · ${race.circuit.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (race.raceDate != null) {
                Text(
                    text = race.raceDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── §2 Season progress ──────────────────────────────────────────────────

@Composable
private fun Section2Season(season: Outcome<Season>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel("Season")
        OutcomeContent(outcome = season) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "Season ${s.year}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ProgressCard(percent = (s.progressPercent * 100).toInt())
                StatCard(label = "GPs completed", value = s.completedGp.toString())
                StatCard(label = "Total km", value = s.totalKm.toString())
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
    nextRace: Outcome<NextRace?>,
    topSpeed: Outcome<TopSpeed?>,
    onClickCircuit: (circuitId: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel("Next weekend")
        OutcomeContent(outcome = nextRace) { race ->
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
    topSpeed: Outcome<TopSpeed?>,
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
                        is Outcome.Success -> {
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
                        is Outcome.Failure -> {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is Outcome.Loading -> {
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

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
            getDriversStandings = wiring.getDriversStandings,
            getConstructorsStandings = wiring.getConstructorsStandings,
            getCircuitTopSpeed = wiring.getCircuitTopSpeed,
            favoritesCache = wiring.favoritesCache,
        )
    )
}
