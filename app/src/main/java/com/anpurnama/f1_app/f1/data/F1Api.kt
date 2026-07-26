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
 * Jolpica is used for result companions and optional pit-stop enrichment.
 * All sources live as Ktor extensions on the same `HttpClient`.
 */
const val F1API_BASE = "https://f1api.dev/api"

const val JOLPICA_BASE = "https://api.jolpi.ca/ergast/f1"
const val JOLPICA_ALPHA_BASE = "https://api.jolpi.ca/f1/alpha"

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

suspend fun HttpClient.getSeason(
    year: Int,
    forceRefresh: Boolean = false,
): SeasonResponseDto {
    val response = get("$F1API_BASE/$year") {
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
 * §3 (favorites team card) and the Leaderboard tab, and the first-launch
 * default-seed (the top constructor's two drivers).
 */
suspend fun HttpClient.getConstructorsChampionship(forceRefresh: Boolean = false): ConstructorsChampionshipResponseDto {
    val response = get("$F1API_BASE/current/constructors-championship") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/**
 * Driver metadata for a given season — the same shape as [getCurrentDrivers]
 * (the f1api.dev catalog uses Ergast canonical ids like `max_verstappen`/
 * `red_bull` for both current and historical seasons). Used as the car-number
 * → Ergast id bridge for the alpha result translator (FP/SQ/SR results carry
 * Jolpica alpha's opaque ids, translated back to canonical at the data seam).
 * Season-matched so past rounds don't suffer car-number reuse across years.
 * HttpCache shares the cost with the favorites/driver-detail flows.
 */
suspend fun HttpClient.getDrivers(
    year: Int,
    forceRefresh: Boolean = false,
): CurrentDriversResponseDto {
    val response = get("$F1API_BASE/$year/drivers") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/** Current driver metadata used by the Driver detail page. */
suspend fun HttpClient.getCurrentDrivers(forceRefresh: Boolean = false): CurrentDriversResponseDto {
    val response = get("$F1API_BASE/current/drivers") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/** Current constructor metadata used by the Team detail page. */
suspend fun HttpClient.getCurrentTeams(forceRefresh: Boolean = false): CurrentTeamsResponseDto {
    val response = get("$F1API_BASE/current/teams") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/** Single source for race results — Jolpica standard `/results.json` (full Ergast richness). */
suspend fun HttpClient.getJolpicaRaceResults(
    year: Int,
    round: Int,
    forceRefresh: Boolean = false,
): JolpicaRaceResultsResponseDto {
    val response = get("$JOLPICA_BASE/$year/$round/results.json") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/** Single source for qualifying results — Jolpica standard `/qualifying.json` (full Ergast richness, Q1/Q2/Q3 segment times). */
suspend fun HttpClient.getJolpicaQualifying(
    year: Int,
    round: Int,
    forceRefresh: Boolean = false,
): JolpicaQualifyingResponseDto {
    val response = get("$JOLPICA_BASE/$year/$round/qualifying.json") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/** Optional race pit-stop enrichment. Missing records are valid empty data. */
suspend fun HttpClient.getJolpicaPitStops(
    year: Int,
    round: Int,
    forceRefresh: Boolean = false,
): JolpicaPitStopsResponseDto {
    val response = get("$JOLPICA_BASE/$year/$round/pitstops.json") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/** Resolve Jolpica alpha's opaque round ID from a season + round number. */
suspend fun HttpClient.getJolpicaAlphaRoundId(
    year: Int,
    round: Int,
    forceRefresh: Boolean = false,
): String? {
    val response = get("$JOLPICA_ALPHA_BASE/core/rounds/") {
        parameter("year", year)
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body<JolpicaAlphaRoundsResponseDto>().data
        .firstOrNull { it.number == round }
        ?.id
}

suspend fun HttpClient.getJolpicaAlphaResults(
    roundId: String,
    filter: String,
    forceRefresh: Boolean = false,
): JolpicaAlphaResultsResponseDto {
    // Sprint (SR), Sprint Qualifying (SQ), and the three Free Practice sessions
    // (FP1/FP2/FP3) are the alpha filter set. An unsupported filter throws
    // "Invalid session filter", which the shared `loadAlpha` caller maps to the
    // not-scheduled Outcome ("Session is unavailable") rather than a hard error.
    require(filter in setOf("SR", "SQ", "FP1", "FP2", "FP3")) { "Invalid session filter" }
    val response = get("$JOLPICA_ALPHA_BASE/results/$roundId/$filter/") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/**
 * Per-circuit metadata from f1api.dev. Distinct from the `Circuit` block
 * inlined on `/current*` responses: this endpoint returns a
 * one-element `circuit: [...]` array and carries the all-time lap record
 * with attribution (`lapRecord` + `fastestLapDriverId/TeamId/Year`) plus
 * `firstParticipationYear`. `circuitLength` is an Int in **meters** (not
 * the `"<N>km"` string form). Drives the Circuit detail page.
 */
suspend fun HttpClient.getCircuit(
    f1apiCircuitId: String,
    forceRefresh: Boolean = false,
): CircuitDetailResponseDto {
    val response = get("$F1API_BASE/circuits/$f1apiCircuitId") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/**
 * Per-circuit P1 results from jolpica. The endpoint path
 * `/circuits/{id}/results/1.json` filters to P1 per race; the
 * aggregation (top driver / top team) is client-side. Server cache is
 * `max-age=3600` (1h) — Ktor HttpCache covers re-opens.
 *
 * **ID translation:** f1api.dev and jolpica use different `circuitId`
 * values for 5 of 24 inlined circuits (see
 * `lode/wayfinder/f1app/circuit-most-wins-api-wrangling.md`). The
 * private map below translates f1api.dev's id to jolpica's at call
 * time; ids not in the map are passed through unchanged (the 19/24
 * match is direct). f1api.dev remains the public `circuitId`
 * everywhere else in the app — the jolpica form never escapes this
 * extension.
 */
suspend fun HttpClient.getCircuitWinners(
    f1apiCircuitId: String,
    forceRefresh: Boolean = false,
): CircuitWinnersResponseDto {
    val jolpicaCircuitId = F1API_TO_JOLPICA_CIRCUIT[f1apiCircuitId] ?: f1apiCircuitId
    val response = get("$JOLPICA_BASE/circuits/$jolpicaCircuitId/results/1.json") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/**
 * 5-entry f1api.dev → jolpica `circuitId` translation. Stable, not
 * data-driven; lives as a private detail of the jolpica adapter. See
 * `lode/wayfinder/f1app/circuit-most-wins-api-wrangling.md` for the
 * full 19/24 namespace match table and the per-circuit race counts.
 */
private val F1API_TO_JOLPICA_CIRCUIT: Map<String, String> = mapOf(
    "austin" to "americas",
    "gilles_villeneuve" to "villeneuve",
    "hermanos_rodriguez" to "rodriguez",
    "lusail" to "losail",
    "montmelo" to "catalunya",
)
