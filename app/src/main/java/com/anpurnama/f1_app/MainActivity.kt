package com.anpurnama.f1_app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anpurnama.f1_app.core.navigation.NavShell
import com.anpurnama.f1_app.core.navigation.Route
import com.anpurnama.f1_app.ui.theme.F1appTheme

class MainActivity : ComponentActivity() {

    // Pending deep-link route. Held as Compose state so a re-emission
    // (e.g. onNewIntent after a widget tap while the app is already
    // foregrounded) re-runs NavShell's LaunchedEffect and pushes
    // the new route onto the backstack.
    //
    // The custom scheme is `f1app://round/{year}/{round}` — built by
    // the Countdown widget from NextRaceCache args. Only the
    // `round` host is in scope; anything else parses to null and is
    // a no-op.
    private var pendingDeepLinkRoute by mutableStateOf<Route?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // edge-to-edge: keep the system from drawing a contrast scrim over the
        // gesture-pill / 3-button nav area — it would wash out the dark
        // surfaceContainer bottom bar. See edge-to-edge skill checklist.
        window.isNavigationBarContrastEnforced = false
        pendingDeepLinkRoute = parseDeepLink(intent)
        setContent {
            F1appTheme {
                NavShell(
                    pendingDeepLink = pendingDeepLinkRoute,
                    onDeepLinkConsumed = { pendingDeepLinkRoute = null },
                )
            }
        }
    }

    /**
     * Fires when a new `ACTION_VIEW` intent arrives on this activity
     * instance. Single-app custom scheme + `launchMode="singleTop"`
     * (manifest) means a widget tap while the app is foregrounded
     * lands here, not a new activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDeepLink(intent)?.let { pendingDeepLinkRoute = it }
    }

    /**
     * Parses `f1app://round/{year}/{round}` into a [Route.RoundDetail]
     * route, or returns `null` for any other URI / malformed path.
     * Called from both onCreate (initial) and onNewIntent (foreground
     * widget tap). The `android.net.Uri` extraction is the only
     * Android-specific step; the actual parsing is in
     * [parseRoundDeepLink] (pure, JVM-testable).
     */
    private fun parseDeepLink(intent: Intent?): Route? {
        val uriString = intent?.data?.toString() ?: return null
        return parseRoundDeepLink(uriString)
    }

    private companion object {
        const val SCHEME = "f1app"
        const val HOST_ROUND = "round"
    }
}

/**
 * Pure parser for the Countdown widget's `f1app://round/{year}/{round}`
 * deep-link URI. Extracted from `MainActivity` so it's testable on
 * the JVM (no `android.net.Uri`, no Robolectric). The caller is
 * responsible for `Uri.toString()`; this function takes the string.
 *
 * Returns a [Route.RoundDetail] when the URI is well-formed and
 * matches the in-scope shape; `null` for any other input. Malformed
 * paths (non-integer year/round, missing host, wrong scheme) all
 * collapse to `null` so `MainActivity` silently no-ops on junk
 * intents.
 */
fun parseRoundDeepLink(uriString: String): Route? {
    val schemeEnd = uriString.indexOf("://")
    if (schemeEnd < 0) return null
    val scheme = uriString.substring(0, schemeEnd)
    if (scheme != "f1app") return null
    val afterScheme = uriString.substring(schemeEnd + 3)
    val pathStart = afterScheme.indexOf('/')
    val hostSegment = if (pathStart < 0) afterScheme else afterScheme.substring(0, pathStart)
    if (hostSegment != "round") return null
    val pathSegment = if (pathStart < 0) "" else afterScheme.substring(pathStart + 1)
    val segments = pathSegment.split('/').filter { it.isNotEmpty() }
    if (segments.size < 2) return null
    val year = segments[0].toIntOrNull() ?: return null
    val round = segments[1].toIntOrNull() ?: return null
    return Route.RoundDetail(year = year, round = round)
}
