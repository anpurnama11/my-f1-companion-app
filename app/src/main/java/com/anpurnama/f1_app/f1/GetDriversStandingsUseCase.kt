package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.JolpicaDriverStandingsResponseDto
import com.anpurnama.f1_app.f1.data.getJolpicaDriverStandings
import com.anpurnama.f1_app.f1.model.DriverStanding
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Drivers' championship standings from Jolpica (`/current/driverStandings.json`).
 * Position-ordered. Drives Homepage §3 (favorites driver cards) and the
 * Leaderboard tab. Also feeds the first-launch default seed in
 * `HomepageViewModel` (the top constructor's two drivers).
 *
 * `forceRefresh = true` adds `Cache-Control: no-cache`. Used by the
 * Homepage pull-to-refresh.
 */
class GetDriversStandingsUseCase(private val client: HttpClient) {
    suspend operator fun invoke(forceRefresh: Boolean = false): Outcome<List<DriverStanding>> = try {
        Outcome.Success(client.getJolpicaDriverStandings(forceRefresh = forceRefresh).toDriverStandings())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

internal fun JolpicaDriverStandingsResponseDto.toDriverStandings(): List<DriverStanding> {
    val entries = mrData.standingsTable.standingsLists
        .firstOrNull()?.driverStandings ?: emptyList()
    return entries.map { entry ->
        val driver = entry.driver
        val team = entry.constructors.firstOrNull()
        DriverStanding(
            driverId = driver.driverId.orEmpty(),
            teamId = team?.constructorId.orEmpty(),
            position = entry.position?.toIntOrNull() ?: 0,
            points = (entry.points?.toDoubleOrNull()?.toInt()) ?: 0,
            wins = entry.wins?.toIntOrNull() ?: 0,
            driverName = listOfNotNull(driver.givenName, driver.familyName)
                .joinToString(" ")
                .ifBlank { driver.code.orEmpty() },
            driverShortName = driver.code,
            driverNumber = driver.permanentNumber?.toIntOrNull(),
            teamName = team?.name,
            name = driver.givenName,
            surname = driver.familyName,
        )
    }
}
