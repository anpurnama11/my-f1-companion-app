package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.RoundResultsResponseDto
import com.anpurnama.f1_app.f1.data.getRoundResults
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.RoundResults
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Per-round race results from f1api.dev `/{year}/{round}/race`. Drives
 * two surfaces (per the ticket 03 spec):
 *  - Round detail full grid (the page uses [RoundResults.results]
 *    directly, in finishing-position order).
 *  - Schedule > Past list podium — sliced `[0..2]` from
 *    [RoundResults.results] via [GetRoundPodiumUseCase]. Same DTO
 *    fetch; HttpCache means the Past list and the drilldown share
 *    the network cost when the user opens a row.
 *
 * `position` is kept as a String per the ticket 03 spec — `NC` for
 * DNF/DNS rows, `"1"`/`"2"` for finishers. Consumers slice the
 * already-ordered array, never sort by `position`.
 *
 * `time` is kept un-parsed: `"1:31:44"` for the winner, `"+22.457"`
 * for the gap, `"+1 lap"` for lapped, `"DNF (1)"` for retirees.
 *
 * Pure Kotlin: only the `HttpClient` (injected by Wiring) crosses the
 * android.* boundary.
 */
class GetRoundResultsUseCase(private val client: HttpClient) {
    suspend operator fun invoke(
        year: Int,
        round: Int,
        forceRefresh: Boolean = false,
    ): Outcome<RoundResults> = try {
        val dto = client.getRoundResults(year, round, forceRefresh = forceRefresh)
        Outcome.Success(dto.toRoundResults())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

/**
 * DTO → domain mapping. The envelope is the unusual `races: {...}`
 * object (not array) shape; `circuit` is a one-element list.
 *
 * `round` comes as a String (`"1"`) on the wire; we coerce to Int at
 * the seam so callers can index without re-parsing.
 *
 * `internal` so the test can reach it from the same module, but it
 * stays off the public API surface. Per the existing convention
 * from ticket 01.
 */
internal fun RoundResultsResponseDto.toRoundResults(): RoundResults {
    val racesDto = races
    val firstCircuit = racesDto.circuit.firstOrNull()
    return RoundResults(
        year = season,
        round = racesDto.round?.toIntOrNull() ?: 0,
        raceName = racesDto.raceName.orEmpty(),
        date = racesDto.date,
        time = racesDto.time,
        circuit = Circuit(
            id = firstCircuit?.circuitId.orEmpty(),
            name = firstCircuit?.circuitName.orEmpty(),
            circuitLengthRaw = firstCircuit?.circuitLength.orEmpty(),
            corners = firstCircuit?.corners,
            city = firstCircuit?.city,
            country = firstCircuit?.country,
        ),
        results = racesDto.results.map { it.toRoundResult() },
    )
}

private fun RoundResultsResponseDto.RacesDto.ResultDto.toRoundResult(): RoundResult = RoundResult(
    position = position.orEmpty(),
    points = points,
    grid = grid.orEmpty(),
    time = time,
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
