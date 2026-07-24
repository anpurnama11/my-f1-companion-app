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
            val qualy: RaceSessionDto? = null,
        ) {
            @Serializable
            data class RaceSessionDto(
                val date: String? = null,
                val time: String? = null,
            )
        }
    }
}

// f1api.dev /current/drivers-championship envelope.
@Serializable
data class DriversChampionshipResponseDto(
    val season: Int = 0,
    @SerialName("drivers_championship") val driversChampionship: List<DriversChampionshipEntryDto> = emptyList(),
) {
    @Serializable
    data class DriversChampionshipEntryDto(
        val driverId: String = "",
        val teamId: String = "",
        val points: Int = 0,
        val position: Int = 0,
        val wins: Int = 0,
        val driver: DriverInfoDto = DriverInfoDto(),
        val team: TeamInfoDto = TeamInfoDto(),
    )

    @Serializable
    data class DriverInfoDto(
        val name: String? = null,
        val surname: String? = null,
        val shortName: String? = null,
        val nationality: String? = null,
        val number: Int? = null,
    )

    @Serializable
    data class TeamInfoDto(
        val teamName: String? = null,
        val country: String? = null,
    )
}

// f1api.dev /current/constructors-championship envelope.
@Serializable
data class ConstructorsChampionshipResponseDto(
    val season: Int = 0,
    @SerialName("constructors_championship") val constructorsChampionship: List<ConstructorsChampionshipEntryDto> = emptyList(),
) {
    @Serializable
    data class ConstructorsChampionshipEntryDto(
        val teamId: String = "",
        val points: Int = 0,
        val position: Int = 0,
        val wins: Int = 0,
        val team: TeamInfoDto = TeamInfoDto(),
    )

    @Serializable
    data class TeamInfoDto(
        val teamName: String? = null,
        val country: String? = null,
    )
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

// OpenF1 /v1/sessions — lowercase-no-underscore field names; @SerialName
// maps each to the snake_case-as-camel Kotlin val. The test harness reuses
// the same `ignoreUnknownKeys` JSON so any extras are absorbed.
@Serializable
data class OpenF1SessionDto(
    @SerialName("session_key") val sessionKey: Int = 0,
    @SerialName("session_name") val sessionName: String? = null,
    @SerialName("date_start") val dateStart: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    val year: Int = 0,
    @SerialName("is_cancelled") val isCancelled: Boolean = false,
)

// OpenF1 /v1/laps — only `st_speed` is read (the others ride along in the
// response but the top-speed cell doesn't surface them).
@Serializable
data class OpenF1LapDto(
    @SerialName("session_key") val sessionKey: Int = 0,
    @SerialName("driver_number") val driverNumber: Int = 0,
    @SerialName("lap_number") val lapNumber: Int = 0,
    @SerialName("st_speed") val stSpeed: Int? = null,
)

@Serializable
data class OpenF1PitStopDto(
    @SerialName("session_key") val sessionKey: Int = 0,
    @SerialName("driver_number") val driverNumber: Int = 0,
    @SerialName("stop_duration") val stopDuration: Double? = null,
)

// OpenF1 /v1/meetings — only `circuit_image` is read for the countdown
// card decorative track layout. `country_flag` rides along for future use.
@Serializable
data class OpenF1MeetingDto(
    @SerialName("meeting_key") val meetingKey: Int = 0,
    @SerialName("meeting_name") val meetingName: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    val year: Int = 0,
    @SerialName("circuit_image") val circuitImage: String? = null,
    @SerialName("country_flag") val countryFlag: String? = null,
)

// f1api.dev /{year}/{round}/race envelope — `races` is an *object* here
// (different from /current's `races: [...]` array), holding a
// one-element `circuit: [...]` array (also different from /current's
// inlined `circuit` object). `results` is ordered by finishing position.
// `position` is a String (`"1"`/`"2"`/`"NC"`); `time` is a dirty String
// (`"1:31:44"`/`"+22.457"`/`"+1 lap"`/`"DNF (1)"`) — both kept
// un-parsed per the ticket 03 spec.
@Serializable
data class RoundResultsResponseDto(
    val season: Int = 0,
    val races: RacesDto = RacesDto(),
) {
    @Serializable
    data class RacesDto(
        val round: String? = null,
        val date: String? = null,
        val time: String? = null,
        val raceId: String = "",
        val raceName: String? = null,
        val url: String? = null,
        val circuit: List<CircuitDto> = emptyList(),
        val results: List<ResultDto> = emptyList(),
    ) {
        @Serializable
        data class CircuitDto(
            val circuitId: String = "",
            val circuitName: String? = null,
            val country: String? = null,
            val city: String? = null,
            val circuitLength: String = "",
            val corners: Int? = null,
        )

        @Serializable
        data class ResultDto(
            val position: String? = null,
            val points: Int = 0,
            val grid: String? = null,
            val time: String? = null,
            val fastLap: String? = null,
            val retired: String? = null,
            val driver: DriverDto = DriverDto(),
            val team: TeamDto = TeamDto(),
        ) {
            @Serializable
            data class DriverDto(
                val driverId: String = "",
                val number: Int? = null,
                val shortName: String? = null,
                val name: String? = null,
                val surname: String? = null,
                val nationality: String? = null,
                val birthday: String? = null,
            )

            @Serializable
            data class TeamDto(
                val teamId: String = "",
                val teamName: String? = null,
                val nationality: String? = null,
                val firstAppareance: Int? = null,
            )
        }
    }
}

// f1api.dev /{year}/{round}/qualy envelope — `races` is an *object* with
// a SINGLE `circuit` object (not a one-element array like /race uses).
// `qualyResults` is ordered by `gridPosition` (1-based Int). q1/q2/q3
// are dirty Strings or null when the driver didn't reach that segment.
@Serializable
data class RoundQualifyingResponseDto(
    val season: Int = 0,
    val races: RacesDto = RacesDto(),
) {
    @Serializable
    data class RacesDto(
        val round: String? = null,
        val qualyDate: String? = null,
        val qualyTime: String? = null,
        val raceId: String = "",
        val raceName: String? = null,
        val url: String? = null,
        val circuit: CircuitDto = CircuitDto(),
        val qualyResults: List<QualyResultDto> = emptyList(),
    ) {
        @Serializable
        data class CircuitDto(
            val circuitId: String = "",
            val circuitName: String? = null,
            val country: String? = null,
            val city: String? = null,
            val circuitLength: String = "",
            val corners: Int? = null,
        )

        @Serializable
        data class QualyResultDto(
            val classificationId: Int? = null,
            val driverId: String = "",
            val teamId: String = "",
            val q1: String? = null,
            val q2: String? = null,
            val q3: String? = null,
            val gridPosition: Int = 0,
            val driver: DriverDto = DriverDto(),
            val team: TeamDto = TeamDto(),
        ) {
            @Serializable
            data class DriverDto(
                val driverId: String = "",
                val number: Int? = null,
                val shortName: String? = null,
                val name: String? = null,
                val surname: String? = null,
                val nationality: String? = null,
                val birthday: String? = null,
            )

            @Serializable
            data class TeamDto(
                val teamId: String = "",
                val teamName: String? = null,
                val nationality: String? = null,
                val firstAppareance: Int? = null,
            )
        }
    }
}

// f1api.dev /{year}/{round}/fp1|fp2|fp3. The endpoint changes only the
// result-array property, so one tolerant DTO handles all three responses.
@Serializable
data class PracticeResponseDto(
    val season: Int = 0,
    val races: PracticeRaceDto = PracticeRaceDto(),
) {
    @Serializable
    data class PracticeRaceDto(
        val round: String? = null,
        val raceName: String? = null,
        val circuit: PracticeCircuitDto = PracticeCircuitDto(),
        val fp1Results: List<PracticeResultDto> = emptyList(),
        val fp2Results: List<PracticeResultDto> = emptyList(),
        val fp3Results: List<PracticeResultDto> = emptyList(),
    )

    @Serializable
    data class PracticeCircuitDto(
        val circuitId: String = "",
        val circuitName: String? = null,
        val circuitLength: String = "",
        val corners: Int? = null,
        val city: String? = null,
        val country: String? = null,
    )

    @Serializable
    data class PracticeResultDto(
        val driverId: String = "",
        val teamId: String = "",
        val time: String? = null,
        val driver: DriverDto = DriverDto(),
        val team: TeamDto = TeamDto(),
    ) {
        @Serializable
        data class DriverDto(
            val driverId: String = "",
            val number: Int? = null,
            val shortName: String? = null,
            val name: String? = null,
            val surname: String? = null,
        )

        @Serializable
        data class TeamDto(
            val teamId: String = "",
            val teamName: String? = null,
        )
    }
}

// Jolpica standard Ergast-compatible race result envelope.
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
        @SerialName("Results") val results: List<ResultDto> = emptyList(),
    )

    @Serializable
    data class ResultDto(
        val number: String? = null,
        val position: String? = null,
        val grid: String? = null,
        val status: String? = null,
        @SerialName("Driver") val driver: DriverDto = DriverDto(),
    )

    @Serializable
    data class DriverDto(
        val driverId: String? = null,
        val permanentNumber: String? = null,
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
