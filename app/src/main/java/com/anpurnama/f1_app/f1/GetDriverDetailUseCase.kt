package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.CurrentDriversResponseDto
import com.anpurnama.f1_app.f1.data.JolpicaDriverStandingsResponseDto
import com.anpurnama.f1_app.f1.data.getCurrentDrivers
import com.anpurnama.f1_app.f1.data.getJolpicaDriverStandings
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
        val championshipResponse = client.getJolpicaDriverStandings(forceRefresh)
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
    championship: JolpicaDriverStandingsResponseDto,
): Outcome<DriverDetail> {
    val driver = drivers.firstOrNull { it.driverId == driverId }
        ?: return Outcome.Failure("Driver not found")
    val entries = championship.mrData.standingsTable.standingsLists
        .firstOrNull()?.driverStandings ?: emptyList()
    val standing = entries
        .firstOrNull { it.driver.driverId == driverId }
        ?.let { entry ->
            val team = entry.constructors.firstOrNull()
            DriverStanding(
                driverId = entry.driver.driverId.orEmpty(),
                teamId = team?.constructorId.orEmpty(),
                position = entry.position?.toIntOrNull() ?: 0,
                points = (entry.points?.toDoubleOrNull()?.toInt()) ?: 0,
                wins = entry.wins?.toIntOrNull() ?: 0,
                driverName = listOfNotNull(entry.driver.givenName, entry.driver.familyName)
                    .joinToString(" ")
                    .ifBlank { entry.driver.code.orEmpty() },
                driverShortName = entry.driver.code,
                driverNumber = entry.driver.permanentNumber?.toIntOrNull(),
                teamName = team?.name,
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
