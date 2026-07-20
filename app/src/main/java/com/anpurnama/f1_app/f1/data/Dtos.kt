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
