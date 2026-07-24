package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.DriversChampionshipResponseDto
import com.anpurnama.f1_app.f1.data.getDriversChampionship
import com.anpurnama.f1_app.f1.model.DriverStanding
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Drivers' championship standings (`/current/drivers-championship`).
 * Position-ordered. Drives Homepage §3 (favorites driver cards) and the
 * Leaderboard tab. Also feeds the first-launch default seed in
 * `HomepageViewModel` (the top constructor's two drivers).
 *
 * `forceRefresh = true` adds `Cache-Control: no-cache`. Used by the
 * Homepage pull-to-refresh.
 */
class GetDriversStandingsUseCase(private val client: HttpClient) {
    suspend operator fun invoke(forceRefresh: Boolean = false): Outcome<List<DriverStanding>> = try {
        Outcome.Success(client.getDriversChampionship(forceRefresh = forceRefresh).toDriverStandings())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

internal fun DriversChampionshipResponseDto.toDriverStandings(): List<DriverStanding> =
    driversChampionship.map { entry ->
        DriverStanding(
            driverId = entry.driverId,
            teamId = entry.teamId,
            position = entry.position,
            points = entry.points,
            wins = entry.wins,
            driverName = listOfNotNull(entry.driver.name, entry.driver.surname)
                .joinToString(" ")
                .ifBlank { entry.driver.shortName.orEmpty() },
            driverShortName = entry.driver.shortName,
            driverNumber = entry.driver.number,
            teamName = entry.team.teamName,
        )
    }
