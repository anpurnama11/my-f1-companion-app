package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.AlphaResultDto
import com.anpurnama.f1_app.f1.data.JolpicaAlphaResultsResponseDto
import com.anpurnama.f1_app.f1.data.getJolpicaAlphaResults
import com.anpurnama.f1_app.f1.data.getJolpicaAlphaRoundId
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.FastestLap
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.SessionResult
import com.anpurnama.f1_app.f1.model.SessionType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

class GetSprintResultUseCase(private val client: HttpClient) {
    suspend operator fun invoke(year: Int, round: Int, forceRefresh: Boolean = false): Outcome<SessionResult> =
        loadAlpha(client, year, round, "SR", SessionType.Sprint, forceRefresh)
}

class GetSprintQualifyingResultUseCase(private val client: HttpClient) {
    suspend operator fun invoke(year: Int, round: Int, forceRefresh: Boolean = false): Outcome<SessionResult> =
        loadAlpha(client, year, round, "SQ", SessionType.SprintQuali, forceRefresh)
}

private suspend fun loadAlpha(
    client: HttpClient,
    year: Int,
    round: Int,
    filter: String,
    session: SessionType,
    forceRefresh: Boolean,
): Outcome<SessionResult> = try {
    val roundId = client.getJolpicaAlphaRoundId(year, round, forceRefresh)
        ?: return Outcome.Failure("Session is unavailable")
    val response = client.getJolpicaAlphaResults(roundId, filter, forceRefresh)
    Outcome.Success(response.toSessionResult(year, round, session))
} catch (e: ClientRequestException) {
    Outcome.Failure("Request failed (${e.response.status.value})")
} catch (e: ServerResponseException) {
    Outcome.Failure("Server error (${e.response.status.value})")
} catch (e: Exception) {
    Outcome.Failure(e.message ?: "Network error")
}

internal fun JolpicaAlphaResultsResponseDto.toSessionResult(
    year: Int,
    round: Int,
    session: SessionType,
): SessionResult {
    val mapped = data.results.map { it.toRoundResult() }
    val circuit = Circuit(
        id = "",
        name = "",
        circuitLengthRaw = "",
        corners = null,
        city = null,
        country = null,
    )
    return if (session == SessionType.SprintQuali) {
        SessionResult(
            year = data.season.year.takeIf { it != 0 } ?: year,
            round = data.round.number.takeIf { it != 0 } ?: round,
            raceName = data.round.name.orEmpty(),
            circuit = circuit,
            session = session,
            qualifyingResults = data.results.map { it.toQualifyingResult() },
        )
    } else {
        SessionResult(
            year = data.season.year.takeIf { it != 0 } ?: year,
            round = data.round.number.takeIf { it != 0 } ?: round,
            raceName = data.round.name.orEmpty(),
            circuit = circuit,
            session = session,
            raceResults = mapped,
            fastestLap = mapped.mapNotNull { result ->
                result.fastLap?.let { time ->
                    FastestLap(result.driverNumber, result.driverName, result.driverShortName, time)
                }
            }.minByOrNull { lap -> lap.time },
        )
    }
}

private fun AlphaResultDto.toRoundResult(): RoundResult {
    val name = listOfNotNull(driver.givenName, driver.familyName)
        .filter(String::isNotBlank).joinToString(" ").ifBlank { driver.id }
    return RoundResult(
        position = positionText ?: position?.toString().orEmpty(),
        points = points?.toInt() ?: 0,
        grid = components.grid?.position?.toString() ?: "0",
        time = time,
        driverId = driver.id,
        driverName = name,
        driverShortName = driver.abbreviation,
        driverNumber = carNumber,
        teamId = team.id,
        teamName = team.name.orEmpty(),
        status = status,
        fastLap = components.fastestLap?.time,
    )
}

private fun AlphaResultDto.toQualifyingResult() = com.anpurnama.f1_app.f1.model.QualifyingResult(
    gridPosition = position ?: 0,
    q1 = components.sq1?.time,
    q2 = components.sq2?.time,
    q3 = components.sq3?.time,
    driverId = driver.id,
    driverName = listOfNotNull(driver.givenName, driver.familyName)
        .filter(String::isNotBlank).joinToString(" ").ifBlank { driver.id },
    driverShortName = driver.abbreviation,
    driverNumber = carNumber,
    teamId = team.id,
    teamName = team.name.orEmpty(),
)
