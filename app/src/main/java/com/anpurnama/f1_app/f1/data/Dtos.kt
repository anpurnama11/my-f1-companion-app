package com.anpurnama.f1_app.f1.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// f1api.dev /current envelope.
// `season` is missing on some endpoints — default to 0 so the mapper is
// tolerant of partial responses (kotlinx.serialization + coerceInputValues
// in the HttpClient JSON config also help).
@Serializable
data class SeasonResponseDto(
    val season: Int = 0,
    val races: List<RaceDto> = emptyList(),
)

@Serializable
data class RaceDto(
    val round: Int = 0,
    val raceName: String? = null,
    val circuit: CircuitDto = CircuitDto(),
    val winner: WinnerDto? = null,
    val laps: Int? = null,
    val schedule: RaceScheduleDto = RaceScheduleDto(),
)

@Serializable
data class RaceScheduleDto(
    val fp1: SessionDto? = null,
    val fp2: SessionDto? = null,
    val fp3: SessionDto? = null,
    val sprintQualy: SessionDto? = null,
    val sprintRace: SessionDto? = null,
    val qualy: SessionDto? = null,
    val race: SessionDto? = null,
)

@Serializable
data class SessionDto(
    val date: String? = null,
    val time: String? = null,
)

@Serializable
data class CircuitDto(
    val circuitId: String = "",
    val circuitName: String? = null,
    val circuitLength: String = "",
    val laps: Int? = null,
    val corners: Int? = null,
    val city: String? = null,
    val country: String? = null,
    // Three spellings across endpoints — @SerialName the one that appears
    // in /current (other endpoints map in their own DTOs).
    @SerialName("firstAppareance") val firstGrandPrix: Int? = null,
)

@Serializable
data class WinnerDto(
    val driverId: String? = null,
    val constructorId: String? = null,
)

// f1api.dev /current/next envelope — the next race only. `race` is a
// single-element array per the contract; an empty list means "no upcoming
// race" (off-season). Inner classes are nested to keep the public surface
// narrow and to make the mapper's reach explicit.
@Serializable
data class NextRaceResponseDto(
    val season: Int = 0,
    val round: Int = 0,
    val race: List<NextRaceInnerDto> = emptyList(),
) {
    @Serializable
    data class NextRaceInnerDto(
        val raceId: String = "",
        val raceName: String? = null,
        val round: Int = 0,
        val laps: Int? = null,
        val circuit: RaceCircuitDto = RaceCircuitDto(),
        val schedule: RaceScheduleDto = RaceScheduleDto(),
    ) {
        @Serializable
        data class RaceCircuitDto(
            val circuitId: String = "",
            val circuitName: String? = null,
            val country: String? = null,
            val city: String? = null,
            val circuitLength: String = "",
            val corners: Int? = null,
        )

        @Serializable
        data class RaceScheduleDto(
            val race: RaceSessionDto? = null,
        ) {
            @Serializable
            data class RaceSessionDto(
                val date: String? = null,
                val time: String? = null,
            )
        }
    }
}

// f1api.dev /current/drivers envelope. The detail endpoint is a separate
// list from the championship endpoint, so the use case joins it by driverId.
@Serializable
data class CurrentDriversResponseDto(
    val season: Int = 0,
    val drivers: List<CurrentDriverDto> = emptyList(),
)

@Serializable
data class CurrentDriverDto(
    val driverId: String = "",
    val name: String? = null,
    val surname: String? = null,
    val nationality: String? = null,
    val birthday: String? = null,
    val number: Int? = null,
    val shortName: String? = null,
    val teamId: String = "",
)

// f1api.dev /current/teams envelope. `firstAppeareance` is the API's
// spelling and is intentionally kept at the wire boundary.
@Serializable
data class CurrentTeamsResponseDto(
    val season: Int = 0,
    val teams: List<CurrentTeamDto> = emptyList(),
)

@Serializable
data class CurrentTeamDto(
    val teamId: String = "",
    val teamName: String? = null,
    val teamNationality: String? = null,
    val firstAppeareance: Int? = null,
    val constructorsChampionships: Int? = null,
    val driversChampionships: Int? = null,
)

// f1api.dev /circuits/{circuitId} envelope. Distinct from /current* which
// inlines a single `circuit` object: this endpoint returns a one-element
// `circuit: [...]` array, and the per-circuit fields carry a different
// shape:
//   - `circuitLength: Int` in **meters** (e.g. 5412 for Bahrain = 5.412 km)
//     — NOT the `"<N>km"` string form used in /current*.
//   - `lapRecord` is the all-time fastest race lap as a wall-clock string
//     (`"1:21:046"`); `fastestLapDriverId`/`fastestLapTeamId`/
//     `fastestLapYear` carry attribution. The driver/team ids match
//     f1api.dev's namespace (e.g. `barrichelo` on Monza's 2004 record).
//   - `firstParticipationYear` is the year the circuit first hosted a GP.
//   - `numberOfCorners` (Int) replaces `/current*`'s `corners` field.
@Serializable
data class CircuitDetailResponseDto(
    val total: Int = 0,
    val circuit: List<CircuitDetailDto> = emptyList(),
) {
    @Serializable
    data class CircuitDetailDto(
        val circuitId: String = "",
        val circuitName: String? = null,
        val country: String? = null,
        val city: String? = null,
        val circuitLength: Int = 0,
        val lapRecord: String? = null,
        val firstParticipationYear: Int? = null,
        val numberOfCorners: Int? = null,
        val fastestLapDriverId: String? = null,
        val fastestLapTeamId: String? = null,
        val fastestLapYear: Int? = null,
        val url: String? = null,
    )
}

// jolpica Ergast-compatible /circuits/{id}/results/1.json envelope. The `1`
// in the path filters to P1 per race; we walk the array client-side and
// groupBy driver/constructor to find the most-winning driver and team.
@Serializable
data class CircuitWinnersResponseDto(
    @SerialName("MRData") val mrData: MrDataDto = MrDataDto(),
) {
    @Serializable
    data class MrDataDto(
        val total: String? = null,
        @SerialName("RaceTable") val raceTable: RaceTableDto = RaceTableDto(),
    )

    @Serializable
    data class RaceTableDto(
        val circuitId: String? = null,
        @SerialName("Races") val races: List<RaceDto> = emptyList(),
    )

    @Serializable
    data class RaceDto(
        val season: String? = null,
        val round: String? = null,
        @SerialName("Results") val results: List<ResultDto> = emptyList(),
    )

    @Serializable
    data class ResultDto(
        val position: String? = null,
        @SerialName("Driver") val driver: DriverDto = DriverDto(),
        @SerialName("Constructor") val constructor: ConstructorDto = ConstructorDto(),
    )

    @Serializable
    data class DriverDto(
        val driverId: String? = null,
        val givenName: String? = null,
        val familyName: String? = null,
    )

    @Serializable
    data class ConstructorDto(
        val constructorId: String? = null,
        val name: String? = null,
    )
}

// Jolpica standard Ergast-compatible race result envelope — the single
// source for race results (see lode/decisions/0006-race-results-hybrid-source.md,
// superseded). Full Ergast richness: per-race Circuit/Location and full Driver
// givenName/familyName/code; per-result points, laps, status, grid, Time
// (millis+time), FastestLap (rank, lap, Time), and the Driver's permanentNumber.
// `driverId`/`constructorId` are the canonical Ergast ids (e.g. "max_verstappen",
// "red_bull") — the same namespace Jolpica pit-stops already use, so the
// race-result ↔ pit-stop join aligns at this boundary.
@Serializable
data class JolpicaRaceResultsResponseDto(
    @SerialName("MRData") val mrData: MrDataDto = MrDataDto(),
) {
    @Serializable
    data class MrDataDto(
        @SerialName("RaceTable") val raceTable: RaceTableDto = RaceTableDto(),
    )

    @Serializable
    data class RaceTableDto(
        val season: String? = null,
        val round: String? = null,
        @SerialName("Races") val races: List<RaceDto> = emptyList(),
    )

    @Serializable
    data class RaceDto(
        val season: String? = null,
        val round: String? = null,
        val raceName: String? = null,
        val date: String? = null,
        val time: String? = null,
        @SerialName("Circuit") val circuit: CircuitDto = CircuitDto(),
        @SerialName("Results") val results: List<ResultDto> = emptyList(),
    )

    @Serializable
    data class CircuitDto(
        val circuitId: String = "",
        val circuitName: String? = null,
        @SerialName("Location") val location: LocationDto = LocationDto(),
    )

    @Serializable
    data class LocationDto(
        val locality: String? = null,
        val country: String? = null,
    )

    @Serializable
    data class ResultDto(
        val number: String? = null,
        val position: String? = null,
        val positionText: String? = null,
        val points: String? = null,
        val grid: String? = null,
        val laps: String? = null,
        val status: String? = null,
        @SerialName("Driver") val driver: DriverDto = DriverDto(),
        @SerialName("Constructor") val constructor: ConstructorDto = ConstructorDto(),
        @SerialName("Time") val time: TimeDto? = null,
        @SerialName("FastestLap") val fastestLap: FastestLapDto? = null,
    )

    @Serializable
    data class DriverDto(
        val driverId: String? = null,
        val permanentNumber: String? = null,
        val code: String? = null,
        val givenName: String? = null,
        val familyName: String? = null,
    )

    @Serializable
    data class ConstructorDto(
        val constructorId: String? = null,
        val name: String? = null,
    )

    @Serializable
    data class TimeDto(
        val millis: String? = null,
        val time: String? = null,
    )

    @Serializable
    data class FastestLapDto(
        val rank: String? = null,
        val lap: String? = null,
        @SerialName("Time") val time: TimeDto? = null,
    )
}

// Jolpica standard Ergast-compatible qualifying envelope — the single
// source for per-round qualifying results (the f1api.dev `/{year}/{round}/qualy`
// fetch is retired; see lode/decisions/0005-session-results-use-two-apis.md,
// to be amended step 7). Full Ergast richness: per-race Circuit/Location and
// full Driver givenName/familyName/code/permanentNumber, plus the per-row
// Constructor (constructorId/name — confirmed present on every row, including
// Q1 knockouts). Qualifying results DO NOT carry `points`/`grid`/`laps`/
// `status`/`Time`/`FastestLap`/`positionText` — only `number`, `position`, and
// the `Q1`/`Q2`/`Q3` segment lap-time Strings (null when the driver didn't reach
// that segment). `driverId`/`constructorId` are the canonical Ergast ids (e.g.
// "max_verstappen", "red_bull") — the same namespace Jolpica pit-stops already
// use, consistent with the race-result mapping in [JolpicaRaceResultsResponseDto].
@Serializable
data class JolpicaQualifyingResponseDto(
    @SerialName("MRData") val mrData: MrDataDto = MrDataDto(),
) {
    @Serializable
    data class MrDataDto(
        @SerialName("RaceTable") val raceTable: RaceTableDto = RaceTableDto(),
    )

    @Serializable
    data class RaceTableDto(
        val season: String? = null,
        val round: String? = null,
        @SerialName("Races") val races: List<RaceDto> = emptyList(),
    )

    @Serializable
    data class RaceDto(
        val season: String? = null,
        val round: String? = null,
        val raceName: String? = null,
        // Ergast qualifying carries the race's `date`/`time` on the Race, not a
        // quali-only schedule — there is no separate qua1y date/time on the wire.
        // These round through to the domain `qualyDate`/`qualyTime` fields, which
        // the UI does not currently render, so the discrepancy is inert.
        val date: String? = null,
        val time: String? = null,
        @SerialName("Circuit") val circuit: CircuitDto = CircuitDto(),
        @SerialName("QualifyingResults") val qualifyingResults: List<QualifyingResultDto> = emptyList(),
    )

    @Serializable
    data class CircuitDto(
        val circuitId: String = "",
        val circuitName: String? = null,
        @SerialName("Location") val location: LocationDto = LocationDto(),
    )

    @Serializable
    data class LocationDto(
        val locality: String? = null,
        val country: String? = null,
    )

    @Serializable
    data class QualifyingResultDto(
        val number: String? = null,
        val position: String? = null,
        @SerialName("Driver") val driver: DriverDto = DriverDto(),
        @SerialName("Constructor") val constructor: ConstructorDto = ConstructorDto(),
        // Lap-time Strings like "1:30.031"; null when the driver was knocked out
        // before this segment. Kept un-parsed.
        @SerialName("Q1") val q1: String? = null,
        @SerialName("Q2") val q2: String? = null,
        @SerialName("Q3") val q3: String? = null,
    )

    @Serializable
    data class DriverDto(
        val driverId: String? = null,
        val permanentNumber: String? = null,
        val code: String? = null,
        val givenName: String? = null,
        val familyName: String? = null,
    )

    @Serializable
    data class ConstructorDto(
        val constructorId: String? = null,
        val name: String? = null,
    )
}

// Jolpica Ergast-compatible /{year}/{round}/pitstops.json envelope.
@Serializable
data class JolpicaPitStopsResponseDto(
    @SerialName("MRData") val mrData: MrDataDto = MrDataDto(),
) {
    @Serializable
    data class MrDataDto(
        @SerialName("RaceTable") val raceTable: RaceTableDto = RaceTableDto(),
    )

    @Serializable
    data class RaceTableDto(
        @SerialName("Races") val races: List<RaceDto> = emptyList(),
    )

    @Serializable
    data class RaceDto(
        @SerialName("PitStops") val pitStops: List<PitStopDto> = emptyList(),
    )

    @Serializable
    data class PitStopDto(
        val driverId: String? = null,
        val duration: String? = null,
    )
}

// ── Jolpica /current/driverStandings.json ─────────────────────
@Serializable
data class JolpicaDriverStandingsResponseDto(
    @SerialName("MRData") val mrData: MrDataDto = MrDataDto(),
) {
    @Serializable
    data class MrDataDto(
        @SerialName("StandingsTable") val standingsTable: StandingsTableDto = StandingsTableDto(),
    )

    @Serializable
    data class StandingsTableDto(
        @SerialName("StandingsLists") val standingsLists: List<StandingsListDto> = emptyList(),
    )

    @Serializable
    data class StandingsListDto(
        @SerialName("DriverStandings") val driverStandings: List<DriverStandingEntryDto> = emptyList(),
    )

    @Serializable
    data class DriverStandingEntryDto(
        val position: String? = null,
        val points: String? = null,
        val wins: String? = null,
        @SerialName("Driver") val driver: DriverDto = DriverDto(),
        @SerialName("Constructors") val constructors: List<ConstructorDto> = emptyList(),
    )

    @Serializable
    data class DriverDto(
        val driverId: String? = null,
        val permanentNumber: String? = null,
        val code: String? = null,
        val givenName: String? = null,
        val familyName: String? = null,
    )

    @Serializable
    data class ConstructorDto(
        val constructorId: String? = null,
        val name: String? = null,
    )
}

// ── Jolpica /current/constructorStandings.json ─────────────────
@Serializable
data class JolpicaConstructorStandingsResponseDto(
    @SerialName("MRData") val mrData: MrDataDto = MrDataDto(),
) {
    @Serializable
    data class MrDataDto(
        @SerialName("StandingsTable") val standingsTable: StandingsTableDto = StandingsTableDto(),
    )

    @Serializable
    data class StandingsTableDto(
        @SerialName("StandingsLists") val standingsLists: List<StandingsListDto> = emptyList(),
    )

    @Serializable
    data class StandingsListDto(
        @SerialName("ConstructorStandings") val constructorStandings: List<ConstructorStandingEntryDto> = emptyList(),
    )

    @Serializable
    data class ConstructorStandingEntryDto(
        val position: String? = null,
        val points: String? = null,
        val wins: String? = null,
        @SerialName("Constructor") val constructor: ConstructorDto = ConstructorDto(),
    )

    @Serializable
    data class ConstructorDto(
        val constructorId: String? = null,
        val name: String? = null,
        val nationality: String? = null,
    )
}

// Jolpica alpha result envelope. The alpha API uses opaque round IDs and
// nests session components under keys such as GRID, FLAP, Q1 and SQ1.
@Serializable
data class JolpicaAlphaRoundsResponseDto(
    val data: List<AlphaRoundDto> = emptyList(),
)

@Serializable
data class AlphaRoundDto(
    val id: String = "",
    val number: Int = 0,
    val name: String? = null,
)

@Serializable
data class JolpicaAlphaResultsResponseDto(
    val data: AlphaResultsDto = AlphaResultsDto(),
)

@Serializable
data class AlphaResultsDto(
    val code: String = "",
    val title: String? = null,
    val season: AlphaSeasonDto = AlphaSeasonDto(),
    val round: AlphaRoundDto = AlphaRoundDto(),
    val results: List<AlphaResultDto> = emptyList(),
)

@Serializable
data class AlphaSeasonDto(val year: Int = 0)

@Serializable
data class AlphaResultDto(
    val driver: AlphaDriverDto = AlphaDriverDto(),
    val team: AlphaTeamDto = AlphaTeamDto(),
    val position: Int? = null,
    val positionText: String? = null,
    val time: String? = null,
    val status: String? = null,
    val points: Double? = null,
    @SerialName("car_number") val carNumber: Int? = null,
    val components: AlphaComponentsDto = AlphaComponentsDto(),
)

@Serializable
data class AlphaDriverDto(
    val id: String = "",
    val abbreviation: String? = null,
    @SerialName("given_name") val givenName: String? = null,
    @SerialName("family_name") val familyName: String? = null,
)

@Serializable
data class AlphaTeamDto(val id: String = "", val name: String? = null)

@Serializable
data class AlphaComponentsDto(
    @SerialName("GRID") val grid: AlphaComponentDto? = null,
    @SerialName("FLAP") val fastestLap: AlphaComponentDto? = null,
    @SerialName("Q1") val q1: AlphaComponentDto? = null,
    @SerialName("Q2") val q2: AlphaComponentDto? = null,
    @SerialName("Q3") val q3: AlphaComponentDto? = null,
    @SerialName("SQ1") val sq1: AlphaComponentDto? = null,
    @SerialName("SQ2") val sq2: AlphaComponentDto? = null,
    @SerialName("SQ3") val sq3: AlphaComponentDto? = null,
)

@Serializable
data class AlphaComponentDto(
    val position: Int? = null,
    val time: String? = null,
)
