package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.ConstructorsChampionshipResponseDto
import com.anpurnama.f1_app.f1.data.getConstructorsChampionship
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Constructors' championship standings (`/current/constructors-championship`).
 * Position-ordered. Drives Homepage §3 (favorites team card) and the
 * Leaderboard tab, and the first-launch default seed in
 * `HomepageViewModel` (#1 constructor + its two drivers).
 */
class GetConstructorsStandingsUseCase(private val client: HttpClient) {
    suspend operator fun invoke(forceRefresh: Boolean = false): Outcome<List<ConstructorStanding>> = try {
        Outcome.Success(client.getConstructorsChampionship(forceRefresh = forceRefresh).toConstructorStandings())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

internal fun ConstructorsChampionshipResponseDto.toConstructorStandings(): List<ConstructorStanding> =
    constructorsChampionship.map { entry ->
        ConstructorStanding(
            teamId = entry.teamId,
            position = entry.position,
            points = entry.points,
            wins = entry.wins,
            teamName = entry.team.teamName.orEmpty(),
            country = entry.team.country,
        )
    }
