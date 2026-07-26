package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.CurrentDriversResponseDto

/**
 * Translates Jolpica alpha's opaque driver/team ids (`driver_…`, `team_…`) into
 * the Ergast canonical ids the rest of the app uses — favorites keys,
 * [com.anpurnama.f1_app.core.navigation.Route.DriverDetail] /
 * [com.anpurnama.f1_app.core.navigation.Route.TeamDetail], Jolpica standard
 * race/quali results (steps 2–3), and Jolpica pitstops all share that one
 * namespace. The bridge is the per-driver **car number**: it is stable and
 * unique within a season and is carried by both the alpha result row
 * (`AlphaResultDto.carNumber`) and the season-matched f1api driver catalog
 * (`CurrentDriverDto.number`).
 *
 * Built once per `loadAlpha` invocation from a [CurrentDriversResponseDto]
 * fetched season-matched (`getDrivers(year)`) so past rounds don't suffer
 * car-number reuse across years. A missing catalog or an unrecognized car
 * number leaves the row's id at its alpha-opaque fallback — the screen still
 * renders; only a future deep link from that row would be unresolved.
 *
 * This translator is data-layer preparation: no SessionResult row renders a
 * deep link or favorites highlight today (every result row — race/quali/FP/SQ/
 * SR — is a plain Card with no onClick). The translated ids land in
 * `RoundResult`/`QualifyingResult`/`PracticeResult` so that when deep links
 * arrive, alpha-sourced rows link into the same canonical ids as race/quali.
 *
 * Namespace reality (verified live): f1api.dev's driver/constructor catalogs
 * (current AND historical) and its championship standings all use Ergast ids
 * (`max_verstappen`, `red_bull`) — the same ids Jolpica standard returns. There
 * is no separate "f1api `maxverstappen`" namespace. So the translator maps
 * alpha → Ergast, aligning FP/SQ/SR rows with favorites, routes, race/quali,
 * and pitstops in one move.
 */
internal data class CarNumberTranslator(
    private val byCarNumber: Map<Int, TranslatedDriver>,
) {
    /** Resolve the Ergast driver+team ids for [carNumber], or null if unknown. */
    internal fun translate(carNumber: Int?): TranslatedDriver? =
        carNumber?.let { byCarNumber[it] }

    internal companion object {
        /**
         * Build a translator from a season-matched driver catalog. Drivers with
         * no number or a blank driverId are skipped; duplicate numbers keep the
         * last entry (car numbers are unique per season, so this is defensive).
         */
        internal fun from(catalog: CurrentDriversResponseDto): CarNumberTranslator =
            CarNumberTranslator(
                byCarNumber = catalog.drivers
                    .filter { it.number != null && it.driverId.isNotBlank() }
                    .associate { it.number!! to TranslatedDriver(it.driverId, it.teamId) },
            )

        /** Empty translator: every lookup misses → callers keep their alpha ids. */
        internal val EMPTY = CarNumberTranslator(emptyMap())
    }
}

/** The Ergast canonical ids for one driver, looked up by car number. */
internal data class TranslatedDriver(val driverId: String, val teamId: String)