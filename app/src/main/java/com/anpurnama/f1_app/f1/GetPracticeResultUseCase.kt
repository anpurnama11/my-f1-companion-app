package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.PracticeResponseDto
import com.anpurnama.f1_app.f1.data.getPracticeResults
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.PracticeResult
import com.anpurnama.f1_app.f1.model.SessionResult
import com.anpurnama.f1_app.f1.model.SessionType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

class GetPracticeResultUseCase(private val client: HttpClient) {
    suspend operator fun invoke(
        year: Int,
        round: Int,
        session: SessionType,
        forceRefresh: Boolean = false,
    ): Outcome<SessionResult> = try {
        require(session in setOf(SessionType.FP1, SessionType.FP2, SessionType.FP3))
        val dto = client.getPracticeResults(year, round, session.shortLabel.lowercase(), forceRefresh)
        Outcome.Success(dto.toSessionResult(year, round, session))
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

internal fun PracticeResponseDto.toSessionResult(
    year: Int,
    round: Int,
    session: SessionType,
): SessionResult {
    val race = races
    val source = when (session) {
        SessionType.FP1 -> race.fp1Results
        SessionType.FP2 -> race.fp2Results
        SessionType.FP3 -> race.fp3Results
        else -> emptyList()
    }
    return SessionResult(
        year = year,
        round = race.round?.toIntOrNull() ?: round,
        raceName = race.raceName.orEmpty(),
        circuit = Circuit(
            id = race.circuit.circuitId,
            name = race.circuit.circuitName.orEmpty(),
            circuitLengthRaw = race.circuit.circuitLength,
            corners = race.circuit.corners,
            city = race.circuit.city,
            country = race.circuit.country,
        ),
        session = session,
        practiceResults = source.mapIndexed { index, result ->
            PracticeResult(
                position = index + 1,
                time = result.time,
                driverId = result.driver.driverId.ifBlank { result.driverId },
                driverName = listOfNotNull(result.driver.name, result.driver.surname)
                    .filter(String::isNotBlank).joinToString(" ")
                    .ifBlank { result.driver.driverId.ifBlank { result.driverId } },
                driverShortName = result.driver.shortName,
                driverNumber = result.driver.number,
                teamId = result.team.teamId.ifBlank { result.teamId },
                teamName = result.team.teamName.orEmpty(),
            )
        },
    )
}
