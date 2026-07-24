package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.getJolpicaPitStops
import com.anpurnama.f1_app.f1.model.FastestPitstop
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/** Optional race-session enrichment; missing pit-stop data is valid null data. */
class GetFastestPitstopUseCase(private val client: HttpClient) {
    suspend operator fun invoke(
        year: Int,
        round: Int,
        forceRefresh: Boolean = false,
    ): Outcome<FastestPitstop?> = try {
        val stops = client.getJolpicaPitStops(year, round, forceRefresh)
            .mrData.raceTable.races
            .flatMap { it.pitStops }
            .mapNotNull { stop ->
                val driverId = stop.driverId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val duration = stop.duration?.toDoubleOrNull()
                    ?.takeIf { it > 0.0 }
                    ?: return@mapNotNull null
                FastestPitstop(driverId, duration)
            }
        Outcome.Success(stops.minByOrNull { it.durationSeconds })
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}
