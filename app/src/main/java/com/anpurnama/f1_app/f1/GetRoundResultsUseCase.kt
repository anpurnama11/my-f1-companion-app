package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.JolpicaRaceResultsResponseDto
import com.anpurnama.f1_app.f1.data.getJolpicaRaceResults
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.RoundResult
import com.anpurnama.f1_app.f1.model.RoundResults
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Per-round race results from Jolpica standard `/ergast/f1/{year}/{round}/
 * results.json` — the single source for race results (ADR 0005 amended;
 * ADR 0006's f1api.dev hybrid merge is superseded; see
 * lode/decisions/0006-race-results-hybrid-source.md).
 * Drives three surfaces:
 *  - Round detail full grid (the page uses [RoundResults.results] directly,
 *    in finishing-position order).
 *  - Schedule > Past list podium — sliced `[0..2]` from [RoundResults.results]
 *    via [GetRoundPodiumUseCase]. Same fetch; HttpCache means the Past list and
 *    the drilldown share the network cost when the user opens a row.
 *  - SessionResult Race screen via [GetSessionResultUseCase] (fastest lap is
 *    derived from per-row [RoundResult.fastLap]).
 *
 * Full Ergast richness renders straight through: `status` (Finished/Lapped/
 * Retired/Did not start), numeric `grid` (0 = pit lane), `Time.time` gap
 * strings, `points`, and per-driver `FastestLap.Time.time`. `RoundResult
 * .driverId`/`teamId` are the canonical Ergast ids, matching Jolpica pit
 * stops; the Round detail circuit stats still come from f1api.dev `getSeason`
 * (the page's primary circuit source), so the thinner Jolpica `Circuit`
 * (id/name/locality/country only — length and corners are absent) is only a
 * fallback when that seasonal fetch hasn't resolved.
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
        val dto = client.getJolpicaRaceResults(year, round, forceRefresh = forceRefresh)
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
 * DTO → domain. The Ergast `RaceTable` carries a single [JolpicaRaceResultsResponseDto
 * .RaceDto] for the requested round; a future/not-yet-run round has an empty
 * `Races` array, in which case we emit a [RoundResults] with an empty result
 * list and a default circuit (Round detail's circuit stats fall back to the
 * seasonal `getSeason` race in that case).
 *
 * `season`/`round` arrive as Strings (`"2026"`, `"5"`) on the wire; coerced to
 * Int at the seam. `position` is kept as a String to match the existing
 * `RoundResult` contract: Ergast `positionText` is numeric for finishers and
 * lapped rows (`"1"`, `"11"`) and a code for non-classified rows (`"R"` retired,
 * `"D"`/`"E"`/`"W"` disqualified/withdrawn); the latter normalize to `"NC"`
 * so the existing screen's `P${position}` + `positionChange()` behavior (arrow
 * hidden on non-numeric) is preserved.
 *
 * `internal` so the test can reach it from the same module, staying off the
 * public API surface. Per the convention from ticket 01.
 */
internal fun JolpicaRaceResultsResponseDto.toRoundResults(): RoundResults {
    val race = mrData.raceTable.races.firstOrNull()
    val circuitDto = race?.circuit
    return RoundResults(
        year = mrData.raceTable.season?.toIntOrNull() ?: 0,
        round = mrData.raceTable.round?.toIntOrNull() ?: 0,
        raceName = race?.raceName.orEmpty(),
        date = race?.date,
        time = race?.time,
        circuit = Circuit(
            id = circuitDto?.circuitId.orEmpty(),
            name = circuitDto?.circuitName.orEmpty(),
            // Jolpica standard has no circuit length/corners — Round detail's
            // circuit stats come from f1api.dev `getSeason` (the page's primary
            // circuit source). These stay blank as a degraded fallback.
            circuitLengthRaw = "",
            corners = null,
            city = circuitDto?.location?.locality,
            country = circuitDto?.location?.country,
        ),
        results = race?.results.orEmpty().map { it.toRoundResult() },
    )
}

private fun JolpicaRaceResultsResponseDto.ResultDto.toRoundResult(): RoundResult {
    val fullName = listOfNotNull(driver.givenName, driver.familyName)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .ifEmpty { driver.driverId.orEmpty() }
    return RoundResult(
        position = displayPositionText(),
        points = points?.toIntOrNull() ?: 0,
        grid = grid.orEmpty(),
        time = time?.time,
        driverId = driver.driverId.orEmpty(),
        driverName = fullName,
        driverShortName = driver.code,
        // `permanentNumber` is the driver's fixed career number; `number` is
        // the car number for that race ( authoritative for pre-permanent-number
        // seasons where `permanentNumber` is absent).
        driverNumber = (driver.permanentNumber ?: number)?.toIntOrNull(),
        teamId = constructor.constructorId.orEmpty(),
        teamName = constructor.name.orEmpty(),
        status = status,
        fastLap = fastestLap?.time?.time,
    )
}

/**
 * Ergast `positionText` → the existing `RoundResult.position` contract:
 * numeric strings pass through (`"1"`, `"11"`); non-classified codes
 * (`"R"`, `"D"`, `"E"`, `"W"`, `"N"`, …) normalize to `"NC"` so the screen
 * keeps its f1api-era `"P NC"` + hidden-arrow behavior for retirees/DNS.
 */
private fun JolpicaRaceResultsResponseDto.ResultDto.displayPositionText(): String {
    val text = positionText ?: position
    if (text.isNullOrBlank()) return ""
    return if (text.toIntOrNull() != null) text else "NC"
}