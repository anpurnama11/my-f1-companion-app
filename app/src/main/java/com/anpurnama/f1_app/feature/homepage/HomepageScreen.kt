package com.anpurnama.f1_app.feature.homepage

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.f1.data.Seasons.currentSeasonYear
import com.anpurnama.f1_app.f1.data.driverImageUrl
import com.anpurnama.f1_app.f1.data.teamImageUrl
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.f1.model.NextRace
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.SessionTime
import com.anpurnama.f1_app.f1.model.WeekendSchedule
import com.anpurnama.f1_app.ui.artwork.CircuitArtwork
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
 *  - §1 Next-race countdown card
 *  - §2 Season progress aggregates (carried over from ticket 01)
 *  - §3 Favorites card plus nearest GP card
 *
 * The screen reads the VM's `UiState.Sections` and renders each section
 * via the shared `OutcomeContent` family — Loading/Failure/Success, each
 * independent. A failure on one section never blanks the others.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomepageScreen(
    onPickFavorites: () -> Unit = {},
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
        sections.weekendSchedule is SectionUiState.Loading

    PullToRefreshBox(
        isRefreshing = anyLoading,
        onRefresh = { viewModel.refresh() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Spacing.normal)
                .padding(top = Spacing.normal),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Section1Countdown(
                nextRace = sections.nextRace,
                weekendSchedule = sections.weekendSchedule,
            )
            Section2Season(sections.season)
            Section3Favorites(
                favorites = sections.favorites,
                drivers = sections.drivers,
                constructors = sections.constructors,
                onPickFavorites = onPickFavorites
            )
        }
    }
}

// ─── §1 Countdown ─────────────────────────────────────────────────────────

@Composable
private fun Section1Countdown(
    nextRace: SectionUiState<NextRace?>,
    weekendSchedule: SectionUiState<WeekendSchedule?>,
) {
    OutcomeContent(state = nextRace) { race ->
        if (race != null) {
            CountdownCard(
                race = race,
                schedule = (weekendSchedule as? SectionUiState.Content)?.data,
            )
        }
    }
}

@Composable
private fun FavoriteLoadError(label: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.normal),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun FavoritesSection(
    favorites: Favorites,
    drivers: List<DriverStanding>,
    constructors: List<ConstructorStanding>,
    onPickFavorites: () -> Unit,
) {
    if (favorites.isEmpty()) {
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
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "No favorites selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pick drivers and a constructor to personalize your homepage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onPickFavorites) {
                    Text("Pick favorites")
                }
            }
        }
        return
    }

    val driver1Id = favorites.driver1Id
    val driver2Id = favorites.driver2Id
    val constructorId = favorites.teamId
    val driver1 = driver1Id?.let { id -> drivers.firstOrNull { it.driverId == id } }
    val driver2 = driver2Id?.let { id -> drivers.firstOrNull { it.driverId == id } }
    val constructor = constructorId?.let { id -> constructors.firstOrNull { it.teamId == id } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FavoriteEntry(
                slot = "Driver 1",
                name = driver1?.driverName?.ifBlank { driver1.driverId }
                    ?: if (driver1Id == null) "Add a driver" else "Unavailable",
                detail = driver1?.let { "${it.driverShortName ?: it.driverId} · #${it.driverNumber ?: "—"}" }
                    ?: driver1Id?.let { "Selected driver is not in current standings" },
                teamId = driver1?.teamId,
                accentTag = "favorite-accent-driver-1",
                driverName = driver1?.name,
                driverSurname = driver1?.surname,
            )
            FavoriteEntry(
                slot = "Driver 2",
                name = driver2?.driverName?.ifBlank { driver2.driverId }
                    ?: if (driver2Id == null) "Add a driver" else "Unavailable",
                detail = driver2?.let { "${it.driverShortName ?: it.driverId} · #${it.driverNumber ?: "—"}" }
                    ?: driver2Id?.let { "Selected driver is not in current standings" },
                teamId = driver2?.teamId,
                accentTag = "favorite-accent-driver-2",
                driverName = driver2?.name,
                driverSurname = driver2?.surname,
            )
            FavoriteEntry(
                slot = "Constructor",
                name = constructor?.teamName?.ifBlank { constructor.teamId }
                    ?: if (constructorId == null) "Add a constructor" else "Unavailable",
                detail = constructor?.country
                    ?: constructorId?.let { "Selected constructor is not in current standings" },
                teamId = constructor?.teamId,
                accentTag = "favorite-accent-constructor",
            )
        }
    }
}

@Composable
private fun FavoriteEntry(
    slot: String,
    name: String,
    detail: String?,
    teamId: String?,
    accentTag: String,
    driverName: String? = null,
    driverSurname: String? = null,
) {
    val year = currentSeasonYear()
    val imageUrl = if (teamId == null) {
        null
    } else if (driverName != null && driverSurname != null) {
        // Driver row: headshot.
        driverImageUrl(driverName, driverSurname, teamId, year)
    } else {
        // Team row: car render.
        teamImageUrl(teamId, year)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(end = Spacing.normal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (imageUrl != null) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(size = 16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.normal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = slot,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name,
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

@Composable
private fun CountdownCard(
    race: NextRace,
    schedule: WeekendSchedule?,
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
                            SessionChip(text = "RACE COMPLETE", strong = true)
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
                val artwork = CircuitArtwork.forId(race.circuit.id)
                Image(
                    painter = painterResource(artwork.resourceId),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = Spacing.sm)
                        .width(140.dp)
                        .height(120.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (artwork.tintable) {
                        ColorFilter.tint(Circuits.forId(race.circuit.id), BlendMode.SrcIn)
                    } else null,
                )
            }
        }
}

@Composable
private fun SessionChip(
    text: String,
    emphasis: Boolean = false,
    strong: Boolean = false,
) {
    val container = when {
        emphasis -> MaterialTheme.colorScheme.primary
        strong -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.outline
    }
    val content = when {
        emphasis -> MaterialTheme.colorScheme.onPrimary
        strong -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val pulseAlpha = if (emphasis) {
        rememberInfiniteTransition(label = "live-indicator").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(750),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "live-indicator-alpha",
        ).value
    } else {
        1f
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emphasis) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(content),
            )
        }
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

/**
 * §2 — season progress as one card. The circular gauge is the lead: a
 * F1Primary arc on a faint outlineVariant track, sweeping from 12 o'clock
 * clockwise. The right column stacks three cumulative stats (GPs / km /
 * laps) as inline label+value rows rather than nested cards, so the
 * gauge is the single visual anchor instead of one of four.
 */
@Composable
private fun Section2Season(season: SectionUiState<Season>) {
    OutcomeContent(state = season) { s ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.normal),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = "Season ${s.year}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressGauge(
                        percent = (s.progressPercent * 100).toInt(),
                        modifier = Modifier.size(144.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        SeasonStatRow(
                            label = "GPs completed",
                            value = s.completedGp.toString(),
                        )
                        SeasonStatRow(
                            label = "Total km covered",
                            value = "%.1f".format(s.totalKm),
                        )
                        SeasonStatRow(
                            label = "Total laps",
                            value = s.totalLaps.toString(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Circular season-progress gauge. Two arcs (track + progress) drawn on a
 * [Canvas] so stroke width, cap, and start angle are explicit: the
 * progress arc starts at 12 o'clock and sweeps clockwise with a round
 * cap, so small percentages stay legible and 100% closes the ring
 * cleanly. The center holds the integer percent + a small "complete"
 * caption — the gauge earns its place by carrying content, not by being
 * decoration. Colors are read in composable scope and captured into the
 * draw lambda so the [Canvas] block stays a [DrawScope] (no theme
 * access in draw lambdas).
 */
@Composable
private fun CircularProgressGauge(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val target = percent.coerceIn(0, 100) / 100f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "seasonProgress",
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.08f
            val pad = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(pad, pad)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor,
            )
            Text(
                text = "complete",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariantColor,
            )
        }
    }
}

@Composable
private fun SeasonStatRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── §3 Nearest GP ────────────────────────────────────────────────────────

@Composable
private fun Section3NearestGp(
    favorites: SectionUiState<Favorites>,
    drivers: SectionUiState<List<DriverStanding>>,
    constructors: SectionUiState<List<ConstructorStanding>>,
    onPickFavorites: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when {
            favorites is SectionUiState.Error -> FavoriteLoadError(
                label = "Favorites unavailable",
                message = favorites.message,
            )
            favorites is SectionUiState.Content &&
                drivers is SectionUiState.Content &&
                constructors is SectionUiState.Content -> FavoritesSection(
                favorites = favorites.data,
                drivers = drivers.data,
                constructors = constructors.data,
                onPickFavorites = onPickFavorites,
            )
            drivers is SectionUiState.Error -> FavoriteLoadError(
                label = "Favorite drivers unavailable",
                message = drivers.message,
            )
            constructors is SectionUiState.Error -> FavoriteLoadError(
                label = "Favorite constructor unavailable",
                message = constructors.message,
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
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
            getDriversStandings = wiring.getDriversStandings,
            getConstructorsStandings = wiring.getConstructorsStandings,
            favoritesCache = wiring.favoritesCache,
        )
    )
}
