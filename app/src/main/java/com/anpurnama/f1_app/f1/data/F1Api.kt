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

/**
 * Per-round race results. Drives Round detail (full grid) and the
 * Schedule > Past list podium (sliced via `GetRoundPodiumUseCase`).
 * The `races` envelope here is the unusual object-with-results shape,
 * distinct from `/current`'s array envelope.
 */
suspend fun HttpClient.getRoundResults(
    year: Int,
    round: Int,
    forceRefresh: Boolean = false,
): RoundResultsResponseDto {
    val response = get("$F1API_BASE/$year/$round/race") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/** Jolpica's authoritative status/grid companion for f1api.dev race data. */
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

/** One of f1api.dev's three practice result endpoints. */
suspend fun HttpClient.getPracticeResults(
    year: Int,
    round: Int,
    session: String,
    forceRefresh: Boolean = false,
): PracticeResponseDto {
    require(session in setOf("fp1", "fp2", "fp3"))
    val response = get("$F1API_BASE/$year/$round/$session") {
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
    require(filter in setOf("SR", "SQ"))
    val response = get("$JOLPICA_ALPHA_BASE/results/$roundId/$filter/") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}

/**
 * Per-round qualifying results. Same envelope shape as the race
 * endpoint but with `qualyResults` (ordered by `gridPosition`) and a
 * single `circuit` object (not a one-element array). Drives Round
 * detail's Qualifying tab.
 */
suspend fun HttpClient.getRoundQualifying(
    year: Int,
    round: Int,
    forceRefresh: Boolean = false,
): RoundQualifyingResponseDto {
    val response = get("$F1API_BASE/$year/$round/qualy") {
        if (forceRefresh) header(HttpHeaders.CacheControl, CacheControl.NO_CACHE)
    }
    return response.body()
}
