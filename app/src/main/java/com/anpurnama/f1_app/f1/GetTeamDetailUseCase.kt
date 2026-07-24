package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.ConstructorsChampionshipResponseDto
import com.anpurnama.f1_app.f1.data.CurrentTeamsResponseDto
import com.anpurnama.f1_app.f1.data.getConstructorsChampionship
import com.anpurnama.f1_app.f1.data.getCurrentTeams
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
        val championshipResponse = client.getConstructorsChampionship(forceRefresh)
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
    championship: ConstructorsChampionshipResponseDto,
): Outcome<TeamDetail> {
    val team = teams.firstOrNull { it.teamId == teamId }
        ?: return Outcome.Failure("Team not found")
    val standing = championship.constructorsChampionship
        .firstOrNull { it.teamId == teamId }
        ?.let { entry ->
            ConstructorStanding(
                teamId = entry.teamId,
                position = entry.position,
                points = entry.points,
                wins = entry.wins,
                teamName = entry.team.teamName.orEmpty(),
                country = entry.team.country,
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
