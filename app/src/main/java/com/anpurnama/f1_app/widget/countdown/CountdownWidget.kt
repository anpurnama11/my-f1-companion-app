package com.anpurnama.f1_app.widget.countdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.ui.theme.Circuits
import com.anpurnama.f1_app.ui.theme.OnSurface
import com.anpurnama.f1_app.ui.theme.OnSurfaceVariant
import com.anpurnama.f1_app.ui.theme.Surface
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Glance home-screen Countdown widget. Reads the [com.anpurnama.f1_app.widget.countdown.data.NextRaceCache]
 * the [CountdownWorker] writes and renders a render-time
 * [CountdownState] — no live chronometer, no second-source fetch.
 *
 * **Layout (dark-only).** Top-to-bottom:
 *  1. Full-bleed ~6dp accent strip in [Circuits.forId] of the
 *     cached circuit (or the neutral fallback for an unknown id).
 *  2. `Surface` body with `Spacing.normal`-style padding containing:
 *     - race name (bold)
 *     - circuit + country
 *     - the state-specific block (countdown, "LIVE NOW",
 *       "RACE COMPLETE", or the off-season / no-cache copy)
 *     - device-local GP date/time
 *
 * **Deep link.** When the state carries a valid `(year, round)`,
 * the body is `clickable` to a `f1app://round/{year}/{round}` intent
 * (parsed in `MainActivity` and pushed as `Route.RoundDetail` onto
 * the Homepage backstack). Suppressed in the no-cache and
 * off-season states where no valid round exists.
 *
 * **Sizing.** `SizeMode.Single` — one layout for all cells. The
 * `AppWidgetProviderInfo` XML in `res/xml/countdown_widget_info.xml`
 * sets the system's `minWidth`/`minHeight`/`resizeMode`.
 */
class CountdownWidget : GlanceAppWidget() {

    // Single layout for all cells. The provider-info XML handles
    // resize boundaries; the visual contract is the same regardless
    // of cell size (the long countdown text wraps to 2 lines on the
    // narrowest allowed width, which the design accepts).
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? F1App
        val cache = app?.wiring?.nextRaceCache
        val snapshot = cache?.snapshot()
        val now = Clock.System.now().toEpochMilliseconds()
        val state = reduceCountdownState(nowMillis = now, snapshot = snapshot)

        provideContent {
            GlanceTheme {
                WidgetContent(state = state)
            }
        }
    }
}

/**
 * Pure presentational composable. The reducer did the work — this
 * just routes to the matching branch and wires the clickable.
 */
@Composable
private fun WidgetContent(state: CountdownState) {
    val (year, round) = deepLinkArgs(state)
    val bodyModifier = if (year != null && round != null) {
        GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Surface))
            .clickable(actionStartActivity(deepLinkIntent(year, round)))
    } else if (state is CountdownState.NoRaceData) {
        GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Surface))
            .clickable(actionRunCallback<RetryAction>())
    } else {
        GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Surface))
    }

    Column(modifier = bodyModifier) {
        AccentStrip(circuitId = state.circuitIdOrNull())
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when (state) {
                is CountdownState.NoRaceData -> NoRaceDataBody()
                is CountdownState.SeasonOver -> SeasonOverBody()
                is CountdownState.Countdown -> CountdownBody(state)
                is CountdownState.LiveNow -> LiveNowBody(state)
                is CountdownState.RaceComplete -> RaceCompleteBody(state)
            }
        }
    }
}

/**
 * 6dp full-bleed accent strip. Paints nothing for the no-cache
 * state (we don't know the circuit) and for the off-season sentinel
 * (we don't want to lie about brand identity during the off-season).
 */
@Composable
private fun AccentStrip(circuitId: String?) {
    if (circuitId == null) {
        Spacer(modifier = GlanceModifier.height(6.dp))
    } else {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(6.dp)
                .background(ColorProvider(Circuits.forId(circuitId))),
        ) {}
    }
}

// -------- body variants --------

@Composable
private fun CountdownBody(state: CountdownState.Countdown) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = state.raceName,
            style = TextStyle(
                color = ColorProvider(OnSurface),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = circuitAndCountry(state.circuitName, state.circuitCountry),
            style = TextStyle(
                color = ColorProvider(OnSurfaceVariant),
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Text(
            text = formatCountdown(state.days, state.hours, state.minutes),
            style = TextStyle(
                color = ColorProvider(OnSurface),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = state.raceStartMillis.toDeviceLocalDateTimeLabel(),
            style = TextStyle(
                color = ColorProvider(OnSurfaceVariant),
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun LiveNowBody(state: CountdownState.LiveNow) {
    val accent = Circuits.forId(state.circuitId)
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = state.raceName,
            style = TextStyle(
                color = ColorProvider(OnSurface),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = circuitAndCountry(state.circuitName, state.circuitCountry),
            style = TextStyle(
                color = ColorProvider(OnSurfaceVariant),
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Text(
            text = "LIVE NOW",
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = state.raceStartMillis.toDeviceLocalDateTimeLabel(),
            style = TextStyle(
                color = ColorProvider(OnSurfaceVariant),
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun RaceCompleteBody(state: CountdownState.RaceComplete) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = state.raceName,
            style = TextStyle(
                color = ColorProvider(OnSurface),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = circuitAndCountry(state.circuitName, state.circuitCountry),
            style = TextStyle(
                color = ColorProvider(OnSurfaceVariant),
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Text(
            text = "RACE COMPLETE",
            style = TextStyle(
                color = ColorProvider(OnSurfaceVariant),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = state.raceStartMillis.toDeviceLocalDateTimeLabel(),
            style = TextStyle(
                color = ColorProvider(OnSurfaceVariant),
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun SeasonOverBody() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Season over",
            style = TextStyle(
                color = ColorProvider(OnSurfaceVariant),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun NoRaceDataBody() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No race data",
            style = TextStyle(
                color = ColorProvider(OnSurface),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap to retry",
            style = TextStyle(
                color = ColorProvider(OnSurfaceVariant),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

// -------- helpers --------

/**
 * Pull the `(year, round)` pair for the deep link out of the
 * state. Returns `(null, null)` when the state has no valid round
 * (no-cache / off-season) — the call site checks both and falls
 * back to the retry-tap or no-click variant accordingly.
 */
private fun deepLinkArgs(state: CountdownState): Pair<Int?, Int?> = when (state) {
    is CountdownState.NoRaceData, is CountdownState.SeasonOver -> null to null
    is CountdownState.Countdown -> state.year to state.round
    is CountdownState.LiveNow -> state.year to state.round
    is CountdownState.RaceComplete -> state.year to state.round
}

private fun deepLinkIntent(year: Int, round: Int): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("f1app://round/$year/$round"))

private fun CountdownState.circuitIdOrNull(): String? = when (this) {
    is CountdownState.NoRaceData, is CountdownState.SeasonOver -> null
    is CountdownState.Countdown -> circuitId
    is CountdownState.LiveNow -> circuitId
    is CountdownState.RaceComplete -> circuitId
}

/**
 * Compose-format the `days/hours/minutes` triple. Drops leading-zero
 * units so a 30-minute countdown reads "30m" not "0d 0h 30m". The
 * design's `Nd Nh Nm` is the unit-suffix pattern, not a literal
 * 3-part format; a glanceable widget benefits from the shorter form.
 */
internal fun formatCountdown(days: Int, hours: Int, minutes: Int): String = when {
    days > 0 -> "${days}d ${hours}h ${minutes}m"
    hours > 0 -> "${hours}h ${minutes}m"
    else -> "${minutes}m"
}

internal fun circuitAndCountry(name: String, country: String?): String =
    if (country.isNullOrBlank()) name else "$name, $country"

/**
 * "Sun 23 Mar · 15:00" in the device's local time zone. Matches the
 * in-app `SessionSlot.toDeviceLocalLabel` format used by the Round
 * detail page, so the widget's GP date/time reads the same as the
 * detail page's.
 */
internal fun Long.toDeviceLocalDateTimeLabel(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val local = Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone)
    val day = local.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
    val month = local.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
    return "$day ${local.dayOfMonth} $month · ${"%02d".format(local.hour)}:${"%02d".format(local.minute)}"
}

/**
 * Tap-to-retry action callback. Enqueues the one-shot
 * [CountdownWorker] so the next attempt writes a fresh cache and
 * repaints the widget. Lives in the same package as the widget
 * because the worker's [enqueueOneTime] is the only thing it needs.
 */
class RetryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        CountdownWorker.enqueueOneTime(context.applicationContext)
    }
}
