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
    val driverschampionship: List<DriversChampionshipEntryDto> = emptyList(),
) {
    @Serializable
    data class DriversChampionshipEntryDto(
        val driverId: String = "",
        val teamId: String = "",
        val points: Int = 0,
        val position: Int = 0,
        val wins: Int = 0,
        val driver: DriverInfoDto = DriverInfoDto(),
    )

    @Serializable
    data class DriverInfoDto(
        val name: String? = null,
        val surname: String? = null,
        val shortName: String? = null,
        val nationality: String? = null,
        val number: Int? = null,
    )
}

// f1api.dev /current/constructors-championship envelope.
@Serializable
data class ConstructorsChampionshipResponseDto(
    val season: Int = 0,
    val constructorschampionship: List<ConstructorsChampionshipEntryDto> = emptyList(),
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
