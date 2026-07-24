package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.RoundResultsResponseDto
import com.anpurnama.f1_app.f1.data.JolpicaRaceResultsResponseDto
import com.anpurnama.f1_app.f1.data.getJolpicaRaceResults
import com.anpurnama.f1_app.f1.data.getRoundResults
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.RoundResults
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Per-round race results from f1api.dev `/{year}/{round}/race`, enriched by
 * Jolpica's authoritative `results.json` status/grid response. Drives
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
        // Jolpica is an authority/enrichment, not a reason to blank the
        // result table when its independent endpoint is unavailable.
        val authority = runCatching {
            client.getJolpicaRaceResults(year, round, forceRefresh = forceRefresh)
        }.getOrNull()
        Outcome.Success(dto.toRoundResults(authority))
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
internal fun RoundResultsResponseDto.toRoundResults(
    authority: JolpicaRaceResultsResponseDto? = null,
): RoundResults {
    val racesDto = races
    val firstCircuit = racesDto.circuit.firstOrNull()
    val authoritativeRows = authority?.mrData?.raceTable?.races
        ?.firstOrNull()
        ?.results
        .orEmpty()
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
        results = racesDto.results.map { result ->
            val authorityRow = authoritativeRows.firstOrNull { row ->
                val f1Number = result.driver.number?.toString()
                f1Number != null && (row.number == f1Number || row.driver.permanentNumber == f1Number)
            } ?: authoritativeRows.firstOrNull { row ->
                row.driver.driverId != null && row.driver.driverId == result.driver.driverId
            }
            result.toRoundResult(authorityRow)
        },
    )
}

private fun RoundResultsResponseDto.RacesDto.ResultDto.toRoundResult(
    authority: JolpicaRaceResultsResponseDto.ResultDto? = null,
): RoundResult = RoundResult(
    position = position.orEmpty(),
    points = points,
    grid = authority?.grid ?: grid.orEmpty(),
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
    status = authority?.status,
    fastLap = fastLap,
)
