package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.CurrentTeamsResponseDto
import com.anpurnama.f1_app.f1.data.JolpicaConstructorStandingsResponseDto
import com.anpurnama.f1_app.f1.data.getCurrentTeams
import com.anpurnama.f1_app.f1.data.getJolpicaConstructorStandings
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.TeamDetail
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException

class GetTeamDetailUseCase(private val client: HttpClient) {
    suspend operator fun invoke(
        teamId: String,
        forceRefresh: Boolean = false,
    ): Outcome<TeamDetail> = try {
        val teamResponse = client.getCurrentTeams(forceRefresh)
        val championshipResponse = client.getJolpicaConstructorStandings(forceRefresh)
        teamResponse.toTeamDetail(teamId, championshipResponse)
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

internal fun CurrentTeamsResponseDto.toTeamDetail(
    teamId: String,
    championship: JolpicaConstructorStandingsResponseDto,
): Outcome<TeamDetail> {
    val team = teams.firstOrNull { it.teamId == teamId }
        ?: return Outcome.Failure("Team not found")
    val entries = championship.mrData.standingsTable.standingsLists
        .firstOrNull()?.constructorStandings ?: emptyList()
    val standing = entries
        .firstOrNull { it.constructor.constructorId == teamId }
        ?.let { entry ->
            ConstructorStanding(
                teamId = entry.constructor.constructorId.orEmpty(),
                position = entry.position?.toIntOrNull() ?: 0,
                points = (entry.points?.toDoubleOrNull()?.toInt()) ?: 0,
                wins = entry.wins?.toIntOrNull() ?: 0,
                teamName = entry.constructor.name.orEmpty(),
                country = entry.constructor.nationality,
            )
        }

    return Outcome.Success(
        TeamDetail(
            teamId = team.teamId,
            wordmark = team.teamName.orEmpty().ifBlank { team.teamId },
            country = team.teamNationality,
            firstAppearance = team.firstAppeareance,
            constructorsChampionships = team.constructorsChampionships,
            driversChampionships = team.driversChampionships,
            standing = standing,
        )
    )
}
