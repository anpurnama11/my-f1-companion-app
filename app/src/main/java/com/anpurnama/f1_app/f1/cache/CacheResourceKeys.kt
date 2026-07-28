package com.anpurnama.f1_app.f1.cache

import com.anpurnama.f1_app.core.cache.CacheResourceKey
import com.anpurnama.f1_app.f1.model.SessionType


enum class SessionResultCacheKind(val wireName: String) {
    Race("race"),
    Qualifying("qualifying"),
    Sprint("sprint"),
    SprintQualifying("sprint-qualifying"),
    Practice1("practice-1"),
    Practice2("practice-2"),
    Practice3("practice-3"),
}

object CacheResourceKeys {
    fun currentSeasonSchedule(season: Int): CacheResourceKey =
        seasonKey(season, "schedule", "season.schedule")

    fun nextRaceSession(season: Int): CacheResourceKey =
        seasonKey(season, "next-race-session", "season.next-race-session")

    fun driverStandings(season: Int): CacheResourceKey =
        seasonKey(season, "standings:drivers", "standings.drivers")

    fun constructorStandings(season: Int): CacheResourceKey =
        seasonKey(season, "standings:constructors", "standings.constructors")

    fun driverCatalog(season: Int): CacheResourceKey =
        seasonKey(season, "catalog:drivers", "catalog.drivers")

    fun constructorCatalog(season: Int): CacheResourceKey =
        seasonKey(season, "catalog:constructors", "catalog.constructors")

    fun sessionResults(
        season: Int,
        round: Int,
        session: SessionResultCacheKind,
    ): CacheResourceKey = seasonKey(
        season = season,
        suffix = "round:$round:session-results:${session.wireName}",
        payloadKind = "session-results.${session.wireName}",
    )

    fun sessionResults(season: Int, round: Int, session: SessionType): CacheResourceKey =
        sessionResults(season, round, session.toCacheKind())

    fun pitstops(season: Int, round: Int): CacheResourceKey =
        seasonKey(season, "round:$round:pitstops", "pitstops")

    fun circuitMetadata(circuitId: String): CacheResourceKey =
        globalKey("circuit:$circuitId:metadata", "circuit.metadata")

    fun circuitMostWins(circuitId: String): CacheResourceKey =
        globalKey("circuit:$circuitId:most-wins", "circuit.most-wins")

    fun wikipediaSummary(pageTitle: String): CacheResourceKey =
        globalKey("wikipedia:summary:${pageTitle.cacheKeySegment()}", "wikipedia.summary")

    private fun seasonKey(season: Int, suffix: String, payloadKind: String) = CacheResourceKey(
        value = "season:$season:$suffix",
        payloadKind = payloadKind,
        season = season,
    )

    private fun globalKey(suffix: String, payloadKind: String) = CacheResourceKey(
        value = suffix,
        payloadKind = payloadKind,
        season = null,
    )
}

private fun SessionType.toCacheKind(): SessionResultCacheKind = when (this) {
    SessionType.FP1 -> SessionResultCacheKind.Practice1
    SessionType.FP2 -> SessionResultCacheKind.Practice2
    SessionType.FP3 -> SessionResultCacheKind.Practice3
    SessionType.SprintQuali -> SessionResultCacheKind.SprintQualifying
    SessionType.Sprint -> SessionResultCacheKind.Sprint
    SessionType.Quali -> SessionResultCacheKind.Qualifying
    SessionType.Race -> SessionResultCacheKind.Race
}

private fun String.cacheKeySegment(): String = trim().lowercase()
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
