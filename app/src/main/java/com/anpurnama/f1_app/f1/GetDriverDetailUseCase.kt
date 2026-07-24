package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.CurrentDriversResponseDto
import com.anpurnama.f1_app.f1.data.DriversChampionshipResponseDto
import com.anpurnama.f1_app.f1.data.getCurrentDrivers
import com.anpurnama.f1_app.f1.data.getDriversChampionship
import com.anpurnama.f1_app.f1.model.DriverDetail
import com.anpurnama.f1_app.f1.model.DriverStanding
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException

class GetDriverDetailUseCase(private val client: HttpClient) {
    suspend operator fun invoke(
        driverId: String,
        forceRefresh: Boolean = false,
    ): Outcome<DriverDetail> = try {
        val driverResponse = client.getCurrentDrivers(forceRefresh)
        val championshipResponse = client.getDriversChampionship(forceRefresh)
        driverResponse.toDriverDetail(driverId, championshipResponse)
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

internal fun CurrentDriversResponseDto.toDriverDetail(
    driverId: String,
    championship: DriversChampionshipResponseDto,
): Outcome<DriverDetail> {
    val driver = drivers.firstOrNull { it.driverId == driverId }
        ?: return Outcome.Failure("Driver not found")
    val standing = championship.driversChampionship
        .firstOrNull { it.driverId == driverId }
        ?.let { entry ->
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
    val teamName = standing?.teamName?.takeIf { it.isNotBlank() } ?: "Unknown team"
    val name = listOfNotNull(driver.name, driver.surname)
        .joinToString(" ")
        .ifBlank { driver.shortName.orEmpty() }

    return Outcome.Success(
        DriverDetail(
            driverId = driver.driverId,
            name = name,
            shortName = driver.shortName,
            nationality = driver.nationality,
            birthday = driver.birthday,
            number = driver.number,
            teamId = standing?.teamId ?: driver.teamId,
            teamName = teamName,
            standing = standing,
        )
    )
}
