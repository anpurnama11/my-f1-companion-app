package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.RoundQualifyingResponseDto
import com.anpurnama.f1_app.f1.data.getRoundQualifying
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.QualifyingResult
import com.anpurnama.f1_app.f1.model.RoundQualifying
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Per-round qualifying results from f1api.dev
 * `/{year}/{round}/qualy`. Drives the Round detail Qualifying
 * section.
 *
 * `qualyResults` is ordered by `gridPosition` (1-based Int — the
 * position the qualifying run earned, not the finishing position).
 * `q1`/`q2`/`q3` are dirty Strings or null when the driver didn't
 * reach that segment; kept un-parsed.
 *
 * Pure Kotlin: only the `HttpClient` (injected by Wiring) crosses the
 * android.* boundary.
 */
class GetRoundQualifyingUseCase(private val client: HttpClient) {
    suspend operator fun invoke(
        year: Int,
        round: Int,
        forceRefresh: Boolean = false,
    ): Outcome<RoundQualifying> = try {
        val dto = client.getRoundQualifying(year, round, forceRefresh = forceRefresh)
        Outcome.Success(dto.toRoundQualifying())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

/**
 * DTO → domain mapping. `circuit` is a single object here (NOT a
 * one-element array like /race uses) — keep the DTO honest and pull
 * it out without a `.first()`.
 *
 * `internal` so the test can reach it from the same module, but it
 * stays off the public API surface. Per the existing convention
 * from ticket 01.
 */
internal fun RoundQualifyingResponseDto.toRoundQualifying(): RoundQualifying {
    val racesDto = races
    return RoundQualifying(
        year = season,
        round = racesDto.round?.toIntOrNull() ?: 0,
        raceName = racesDto.raceName.orEmpty(),
        qualyDate = racesDto.qualyDate,
        qualyTime = racesDto.qualyTime,
        circuit = Circuit(
            id = racesDto.circuit.circuitId,
            name = racesDto.circuit.circuitName.orEmpty(),
            circuitLengthRaw = racesDto.circuit.circuitLength,
            corners = racesDto.circuit.corners,
            city = racesDto.circuit.city,
            country = racesDto.circuit.country,
        ),
        results = racesDto.qualyResults.map { it.toQualifyingResult() },
    )
}

private fun RoundQualifyingResponseDto.RacesDto.QualyResultDto.toQualifyingResult(): QualifyingResult = QualifyingResult(
    gridPosition = gridPosition,
    q1 = q1,
    q2 = q2,
    q3 = q3,
    driverId = driver.driverId,
    driverName = listOfNotNull(driver.name, driver.surname)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifEmpty { driver.driverId },
    driverShortName = driver.shortName,
    driverNumber = driver.number,
    teamId = team.teamId,
    teamName = team.teamName.orEmpty(),
)
