package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.JolpicaQualifyingResponseDto
import com.anpurnama.f1_app.f1.data.getJolpicaQualifying
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.QualifyingResult
import com.anpurnama.f1_app.f1.model.RoundQualifying
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Per-round qualifying results from Jolpica standard
 * `/ergast/f1/{year}/{round}/qualifying.json` — the single source for
 * qualifying results (the f1api.dev `/{year}/{round}/qualy` fetch is retired;
 * see lode/decisions/0005-session-results-use-two-apis.md, amended).
 * Drives the Round detail Qualifying section and the SessionResult Quali screen
 * via [GetSessionResultUseCase].
 *
 * Full Ergast richness renders straight through: `position` (the qualifying-only
 * 1-based grid-earned ordinal), per-segment `Q1`/`Q2`/`Q3` lap-time Strings
 * (null when the driver was knocked out before that segment), Driver
 * `givenName`/`familyName`/`code`/`permanentNumber`, and the per-row Constructor
 * `constructorId`/`name` (present on every row, including Q1 knockouts).
 * `QualifyingResult.driverId`/`teamId` are the canonical Ergast ids, matching
 * Jolpica pit-stops and the race-result mapping — consistent at this boundary.
 * Jolpica standard qualifying carries no `points`/`grid`/`laps`/`status`/
 * `Time`/`FastestLap`; those race-only fields stay absent from the quali domain.
 *
 * The Ergast Race's `date`/`time` are the race's scheduled values (there is no
 * separate quali date/time on the wire); they round through to the domain
 * `qualyDate`/`qualyTime` fields, which the UI does not currently render, so
 * the discrepancy is inert. The Circuit block carries only id/name/locality/
 * country (no length/corners); Round detail's circuit stats fall back to the
 * seasonal `getSeason` race, so the thinner Jolpica Circuit is only a degraded
 * fallback — `circuitLengthRaw`/`corners` stay blank.
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
        val dto = client.getJolpicaQualifying(year, round, forceRefresh = forceRefresh)
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
 * DTO → domain. The Ergast `RaceTable` carries a single
 * [JolpicaQualifyingResponseDto.RaceDto] for the requested round; a
 * future/not-yet-run round has an empty `Races` array, in which case we emit a
 * [RoundQualifying] with an empty result list and a default circuit (Round
 * detail's circuit stats fall back to the seasonal `getSeason` race in that case).
 *
 * `season`/`round` arrive as Strings (`"2026"`, `"5"`) on the wire; coerced to
 * Int at the seam. `position` is the qualifying ordinal (always numeric on the
 * wire — qualifying has no `positionText`/non-classified codes the way the race
 * endpoint does); it parses to the existing `QualifyingResult.gridPosition`
 * Int contract, with a `0` defensive fallback that should never fire.
 *
 * `internal` so the test can reach it from the same module, staying off the
 * public API surface. Per the convention from ticket 01.
 */
internal fun JolpicaQualifyingResponseDto.toRoundQualifying(): RoundQualifying {
    val race = mrData.raceTable.races.firstOrNull()
    val circuitDto = race?.circuit
    return RoundQualifying(
        year = mrData.raceTable.season?.toIntOrNull() ?: 0,
        round = mrData.raceTable.round?.toIntOrNull() ?: 0,
        raceName = race?.raceName.orEmpty(),
        // Ergast qualifying has no separate quali date/time; the race's
        // `date`/`time` round through (unused by the UI — see class doc).
        qualyDate = race?.date,
        qualyTime = race?.time,
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
        results = race?.qualifyingResults.orEmpty().map { it.toQualifyingResult() },
    )
}

private fun JolpicaQualifyingResponseDto.QualifyingResultDto.toQualifyingResult(): QualifyingResult {
    val fullName = listOfNotNull(driver.givenName, driver.familyName)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .ifEmpty { driver.driverId.orEmpty() }
    return QualifyingResult(
        gridPosition = position?.toIntOrNull() ?: 0,
        q1 = q1,
        q2 = q2,
        q3 = q3,
        driverId = driver.driverId.orEmpty(),
        driverName = fullName,
        driverShortName = driver.code,
        // `permanentNumber` is the driver's fixed career number; `number` is the
        // car number for that race (authoritative for pre-permanent-number seasons
        // where `permanentNumber` is absent).
        driverNumber = (driver.permanentNumber ?: number)?.toIntOrNull(),
        teamId = constructor.constructorId.orEmpty(),
        teamName = constructor.name.orEmpty(),
    )
}