package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.JolpicaConstructorStandingsResponseDto
import com.anpurnama.f1_app.f1.data.getJolpicaConstructorStandings
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Constructors' championship standings from Jolpica
 * (`/current/constructorStandings.json`). Position-ordered. Drives
 * Homepage §3 (favorites team card) and the Leaderboard tab, and the
 * first-launch default seed in `HomepageViewModel` (#1 constructor +
 * its two drivers).
 */
class GetConstructorsStandingsUseCase(private val client: HttpClient) {
    suspend operator fun invoke(forceRefresh: Boolean = false): Outcome<List<ConstructorStanding>> = try {
        Outcome.Success(client.getJolpicaConstructorStandings(forceRefresh = forceRefresh).toConstructorStandings())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

internal fun JolpicaConstructorStandingsResponseDto.toConstructorStandings(): List<ConstructorStanding> {
    val entries = mrData.standingsTable.standingsLists
        .firstOrNull()?.constructorStandings ?: emptyList()
    return entries.map { entry ->
        ConstructorStanding(
            teamId = entry.constructor.constructorId.orEmpty(),
            position = entry.position?.toIntOrNull() ?: 0,
            points = (entry.points?.toDoubleOrNull()?.toInt()) ?: 0,
            wins = entry.wins?.toIntOrNull() ?: 0,
            teamName = entry.constructor.name.orEmpty(),
            country = entry.constructor.nationality,
        )
    }
}
