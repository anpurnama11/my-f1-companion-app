package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.SeasonResponseDto
import com.anpurnama.f1_app.f1.data.getCurrent
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.Season
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Returns the current season (full schedule + pre-computed aggregates).
 *
 * `forceRefresh = true` adds a `Cache-Control: no-cache` header so the
 * request bypasses the HttpCache. Used by the Homepage pull-to-refresh.
 *
 * Pure Kotlin: only the `HttpClient` (injected by Wiring) crosses the
 * android.* boundary.
 */
class GetSeasonUseCase(private val client: HttpClient) {
    suspend operator fun invoke(forceRefresh: Boolean = false): Outcome<Season> = try {
        val dto = client.getCurrent(forceRefresh = forceRefresh)
        Outcome.Success(dto.toSeason())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

/**
 * DTO → domain mapping. Aggregates are pre-computed here so the
 * ViewModel never re-walks the list. Per the spec, `circuitLength` is
 * stored raw on the model and only digit-stripped for the `totalKm`
 * sum — same parse applied to every completed race.
 *
 * Visible for testing (internal); not exposed as public API.
 */
internal fun SeasonResponseDto.toSeason(): Season {
    val completed = races.filter { it.winner != null }
    val totalKm = completed.sumOf { race ->
        race.circuit.circuitLength.filter(Char::isDigit).toIntOrNull() ?: 0
    }
    val totalLaps = completed.sumOf { it.laps ?: 0 }
    val progress = if (races.isEmpty()) {
        0f
    } else {
        completed.size.toFloat() / races.size
    }
    return Season(
        year = season,
        races = races.map { it.toRace() },
        completedGp = completed.size,
        totalKm = totalKm,
        totalLaps = totalLaps,
        progressPercent = progress,
    )
}

private fun com.anpurnama.f1_app.f1.data.RaceDto.toRace(): Race = Race(
    round = round,
    name = raceName.orEmpty(),
    circuit = Circuit(
        id = circuit.circuitId,
        name = circuit.circuitName.orEmpty(),
        circuitLengthRaw = circuit.circuitLength,
        corners = circuit.corners,
        city = circuit.city,
        country = circuit.country,
    ),
    winnerId = winner?.driverId,
    laps = laps,
)
