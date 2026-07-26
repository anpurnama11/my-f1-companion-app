package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.AlphaResultDto
import com.anpurnama.f1_app.f1.data.JolpicaAlphaResultsResponseDto
import com.anpurnama.f1_app.f1.data.getDrivers
import com.anpurnama.f1_app.f1.data.getJolpicaAlphaResults
import com.anpurnama.f1_app.f1.data.getJolpicaAlphaRoundId
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.FastestLap
import com.anpurnama.f1_app.f1.model.PracticeResult
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

/**
 * Shared Jolpica alpha loader for the sprint and free-practice sessions.
 * `internal` so the sprint and practice use cases share one fetch path.
 *
 * Not-scheduled handling: a round with no alpha entry resolves `roundId = null`
 * and returns "Session is unavailable". The `require` guard in
 * [getJolpicaAlphaResults] rejects an unsupported `filter` with
 * "Invalid session filter" (an [IllegalArgumentException]); that is mapped
 * here to the same not-scheduled outcome rather than surfacing a hard error,
 * so a session type that isn't on the alpha calendar fails gracefully.
 */
internal suspend fun loadAlpha(
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
    // Season-matched driver catalog is the car-number → Ergast id bridge for the
    // alpha translator. A catalog fetch failure (4xx/5xx/network) leaves the
    // translator empty; rows keep their alpha-opaque ids and the screen still
    // renders (only a future deep link would be unresolved). Cancellation is
    // rethrown — a cancelled load must NOT be silently turned into a degraded
    // success (runCatching would swallow CancellationException; this try/catch
    // avoids that coroutine bug).
    val translator = try {
        CarNumberTranslator.from(client.getDrivers(year, forceRefresh))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        CarNumberTranslator.EMPTY
    }
    Outcome.Success(response.toSessionResult(year, round, session, translator))
} catch (e: IllegalArgumentException) {
    // The alpha filter guard throws "Invalid session filter". Match its message
    // precisely: kotlinx.serialization's `SerializationException` is itself an
    // `IllegalArgumentException`, so a broad catch here would mask a
    // malformed-alpha-response error as "not scheduled". A non-guard IAE
    // (e.g. a deserialization failure) falls to the `else` and surfaces its real
    // message instead.
    if (e.message == "Invalid session filter") {
        Outcome.Failure("Session is unavailable")
    } else {
        Outcome.Failure(e.message ?: "Network error")
    }
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
    translator: CarNumberTranslator,
): SessionResult {
    val mapped = data.results.map { it.toRoundResult(translator) }
    val circuit = Circuit(
        id = "",
        name = "",
        circuitLengthRaw = "",
        corners = null,
        city = null,
        country = null,
    )
    val yearOut = data.season.year.takeIf { it != 0 } ?: year
    val roundOut = data.round.number.takeIf { it != 0 } ?: round
    val raceName = data.round.name.orEmpty()
    // Exhaustive over SessionType (no `else`): adding a session type that can
    // reach loadAlpha is a compile error here forcing an explicit decision,
    // rather than silently routing into an unintended branch. Sprint/Race/Quali
    // share the race-style mapping, but only Sprint can actually reach this
    // branch via loadAlpha (Race/Quali use the Jolpica standard path, not alpha).
    return when (session) {
        SessionType.SprintQuali -> SessionResult(
            year = yearOut,
            round = roundOut,
            raceName = raceName,
            circuit = circuit,
            session = session,
            qualifyingResults = data.results.map { it.toQualifyingResult(translator) },
        )
        SessionType.FP1, SessionType.FP2, SessionType.FP3 -> SessionResult(
            year = yearOut,
            round = roundOut,
            raceName = raceName,
            circuit = circuit,
            session = session,
            // Alpha practice rows carry `position`/`time`/`car_number`/driver/team
            // with empty `components` ({}) — no GRID/FLAP segments. Map to the
            // practice domain list, taking `position` with a 1-based index fallback.
            practiceResults = data.results.mapIndexed { index, r -> r.toPracticeResult(index, translator) },
        )
        SessionType.Sprint, SessionType.Race, SessionType.Quali -> SessionResult(
            year = yearOut,
            round = roundOut,
            raceName = raceName,
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

private fun AlphaResultDto.toPracticeResult(index: Int, translator: CarNumberTranslator): PracticeResult {
    val translated = translator.translate(carNumber)
    val name = listOfNotNull(driver.givenName, driver.familyName)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .ifBlank { driver.id }
    return PracticeResult(
        position = position ?: index + 1,
        time = time,
        // Alpha opaque ids → Ergast canonical via the car-number bridge; fall back
        // to the opaque id so the row still renders if the catalog missed.
        driverId = translated?.driverId ?: driver.id,
        driverName = name,
        driverShortName = driver.abbreviation,
        driverNumber = carNumber,
        teamId = translated?.teamId ?: team.id,
        teamName = team.name.orEmpty(),
    )
}

private fun AlphaResultDto.toRoundResult(translator: CarNumberTranslator): RoundResult {
    val translated = translator.translate(carNumber)
    val name = listOfNotNull(driver.givenName, driver.familyName)
        .filter(String::isNotBlank).joinToString(" ").ifBlank { driver.id }
    return RoundResult(
        position = positionText ?: position?.toString().orEmpty(),
        points = points?.toInt() ?: 0,
        grid = components.grid?.position?.toString() ?: "0",
        time = time,
        // Alpha opaque ids → Ergast canonical via the car-number bridge; fall back
        // to the opaque id so the row still renders if the catalog missed.
        driverId = translated?.driverId ?: driver.id,
        driverName = name,
        driverShortName = driver.abbreviation,
        driverNumber = carNumber,
        teamId = translated?.teamId ?: team.id,
        teamName = team.name.orEmpty(),
        status = status,
        fastLap = components.fastestLap?.time,
    )
}

private fun AlphaResultDto.toQualifyingResult(translator: CarNumberTranslator) =
    com.anpurnama.f1_app.f1.model.QualifyingResult(
        gridPosition = position ?: 0,
        q1 = components.sq1?.time,
        q2 = components.sq2?.time,
        q3 = components.sq3?.time,
        driverId = translator.translate(carNumber)?.driverId ?: driver.id,
        driverName = listOfNotNull(driver.givenName, driver.familyName)
            .filter(String::isNotBlank).joinToString(" ").ifBlank { driver.id },
        driverShortName = driver.abbreviation,
        driverNumber = carNumber,
        teamId = translator.translate(carNumber)?.teamId ?: team.id,
        teamName = team.name.orEmpty(),
    )
