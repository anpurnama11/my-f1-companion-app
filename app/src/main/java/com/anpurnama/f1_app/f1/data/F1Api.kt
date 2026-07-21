package com.anpurnama.f1_app.f1.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.utils.CacheControl
import io.ktor.http.HttpHeaders

/**
 * Base URL for the f1api.dev primary source. Held by `HttpClientFactory` /
 * `Wiring`; full URLs are built per request.
 *
 * Other sources (OpenF1 for top-speed, jolpica for all-time
 * most-wins-at-circuit) are added below — per ticket 04's multi-source
 * contract, all sources live as Ktor extensions on the same `HttpClient`,
 * no separate packages.
 */
const val F1API_BASE = "https://f1api.dev/api"

/**
 * OpenF1 base URL. Used for the top-speed stat (Homepage §3, Round detail).
 * Sends no cache headers (nginx, no CDN) — HttpCache skips it; accepted
 * uncached. Per ticket 04 / 11.
 */
const val OPENF1_BASE = "https://api.openf1.org/v1"

/**
 * Full-season schedule + sessions. Used by Homepage §2 aggregates and the
 * Schedule tab.
 */
suspend fun HttpClient.getCurrent(forceRefresh: Boolean = false): SeasonResponseDto {
    val response = get("$F1API_BASE/current") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/**
 * Next race (single-row envelope: `race: [ {...} ]`). Used by Homepage §1
 * (favorites GP card) and §3 (nearest-GP info), and by the Countdown
 * worker (ticket 07).
 */
suspend fun HttpClient.getNextRace(forceRefresh: Boolean = false): NextRaceResponseDto {
    val response = get("$F1API_BASE/current/next") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/**
 * Drivers' championship standings, position-ordered. Used by Homepage §1
 * (favorites driver cards) and the Leaderboard tab.
 */
suspend fun HttpClient.getDriversChampionship(forceRefresh: Boolean = false): DriversChampionshipResponseDto {
    val response = get("$F1API_BASE/current/drivers-championship") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/**
 * Constructors' championship standings, position-ordered. Used by Homepage
 * §1 (favorites team card) and the Leaderboard tab, and the first-launch
 * default-seed (the top constructor's two drivers).
 */
suspend fun HttpClient.getConstructorsChampionship(forceRefresh: Boolean = false): ConstructorsChampionshipResponseDto {
    val response = get("$F1API_BASE/current/constructors-championship") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/**
 * OpenF1 sessions filtered by year + country + session name. Used to
 * resolve the `session_key` for `GetCircuitTopSpeedUseCase`. Returns all
 * matching sessions (caller filters to the exact race date — multi-circuit
 * countries like US/Spain/Italy return N per year).
 *
 * OpenF1 sends no cache headers, so `forceRefresh` is omitted — there's
 * no cache to bust.
 */
suspend fun HttpClient.getOpenF1Sessions(
    year: Int,
    countryName: String,
    sessionName: String? = null,
): List<OpenF1SessionDto> {
    val response = get("$OPENF1_BASE/sessions") {
        parameter("year", year)
        parameter("country_name", countryName)
        if (sessionName != null) parameter("session_name", sessionName)
    }
    return response.body()
}

/**
 * OpenF1 laps for a session. `GetCircuitTopSpeedUseCase` takes
 * `max(lap.stSpeed for lap in laps)`. Sends no cache headers — see
 * [getOpenF1Sessions] for the why.
 */
suspend fun HttpClient.getOpenF1Laps(sessionKey: Int): List<OpenF1LapDto> {
    val response = get("$OPENF1_BASE/laps") {
        parameter("session_key", sessionKey)
    }
    return response.body()
}

/**
 * OpenF1 meetings filtered by year + country. `GetCircuitImageUseCase`
 * reads `circuit_image` for the countdown card track-layout image.
 * Sends no cache headers — OpenF1 is nginx, no CDN.
 */
suspend fun HttpClient.getOpenF1Meetings(
    year: Int,
    countryName: String,
): List<OpenF1MeetingDto> {
    val response = get("$OPENF1_BASE/meetings") {
        parameter("year", year)
        parameter("country_name", countryName)
    }
    return response.body()
}

/**
 * Country string divergence between f1api.dev's `circuit.country` and
 * OpenF1's `country_name`. Only one entry in the current 24-circuit
 * schedule (Silverstone: "Great Britain" vs "United Kingdom"); applied
 * only when the literal `country_name` returns 0 sessions. Per
 * ticket 11 research.
 */
internal val F1API_TO_OPENF1_COUNTRY: Map<String, String> = mapOf(
    "Great Britain" to "United Kingdom",
)
