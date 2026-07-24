package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.NextRaceResponseDto
import com.anpurnama.f1_app.f1.data.getNextRace
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.NextRace
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Next race (`/current/next`). Drives Homepage §1 (countdown card)
 * and §3 (nearest-GP info). Also feeds the Countdown worker (when that
 * lands in ticket 07).
 *
 * `forceRefresh = true` adds `Cache-Control: no-cache` so the request
 * bypasses the HttpCache. Used by the Homepage pull-to-refresh.
 *
 * `NextRaceResponseDto.toNextRace()` returns `null` for an empty `race`
 * list (off-season). An off-season call is the success path with a null
 * payload; the screen renders the empty state and the rest of the
 * homepage keeps working.
 *
 * Pure Kotlin: only the `HttpClient` (injected by Wiring) crosses the
 * android.* boundary.
 */
class GetNextRaceUseCase(private val client: HttpClient) {
    suspend operator fun invoke(forceRefresh: Boolean = false): Outcome<NextRace?> = try {
        Outcome.Success(client.getNextRace(forceRefresh = forceRefresh).toNextRace())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

/**
 * DTO → domain mapping. The `NextRace` carries the country + Qualifying
 * date the OpenF1 join in `GetCircuitTopSpeedUseCase` reads — those are
 * load-bearing for §3.
 *
 * `internal` so the test can reach it from the same module, but it stays
 * off the public API surface. Per the existing convention from ticket 01.
 */
internal fun NextRaceResponseDto.toNextRace(): NextRace? = race.firstOrNull()?.let { inner ->
    NextRace(
        year = season,
        round = inner.round,
        raceName = inner.raceName.orEmpty(),
        raceId = inner.raceId,
        laps = inner.laps,
        circuit = Circuit(
            id = inner.circuit.circuitId,
            name = inner.circuit.circuitName.orEmpty(),
            circuitLengthRaw = inner.circuit.circuitLength,
            corners = inner.circuit.corners,
            city = inner.circuit.city,
            country = inner.circuit.country,
        ),
        raceDate = inner.schedule.race?.date,
        qualyDate = inner.schedule.qualy?.date,
        raceTime = inner.schedule.race?.time,
    )
}
