package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.F1API_TO_OPENF1_COUNTRY
import com.anpurnama.f1_app.f1.data.getOpenF1PitStops
import com.anpurnama.f1_app.f1.data.getOpenF1Sessions
import com.anpurnama.f1_app.f1.data.getRoundResults
import com.anpurnama.f1_app.f1.model.FastestPitstop
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/** Optional Race-session enrichment; missing pit data is a valid null result. */
class GetFastestPitstopUseCase(private val client: HttpClient) {
    suspend operator fun invoke(
        year: Int,
        round: Int,
        forceRefresh: Boolean = false,
    ): Outcome<FastestPitstop?> = try {
        val race = client.getRoundResults(year, round, forceRefresh)
        val country = race.races.circuit.firstOrNull()?.country.orEmpty()
        val date = race.races.date
        val sessions = findSessions(year, country, date)
        val sessionKey = sessions.firstOrNull()?.sessionKey ?: return Outcome.Success(null)
        val fastest = client.getOpenF1PitStops(sessionKey)
            .mapNotNull { stop ->
                val duration = stop.stopDuration
                if (duration != null && duration > 0.0) {
                    FastestPitstop(stop.driverNumber, duration)
                } else null
            }
            .minByOrNull { it.durationSeconds }
        Outcome.Success(fastest)
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }

    private suspend fun findSessions(
        year: Int,
        country: String,
        date: String?,
    ): List<com.anpurnama.f1_app.f1.data.OpenF1SessionDto> {
        val candidates = listOfNotNull(country, F1API_TO_OPENF1_COUNTRY[country])
        for (candidate in candidates) {
            val matches = client.getOpenF1Sessions(year, candidate, "Race")
                .filter { it.dateStart?.take(10) == date }
            if (matches.isNotEmpty()) return matches
        }
        return emptyList()
    }
}
