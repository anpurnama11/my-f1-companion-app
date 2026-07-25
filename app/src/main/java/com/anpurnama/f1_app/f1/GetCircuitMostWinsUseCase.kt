package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.CircuitWinnersResponseDto
import com.anpurnama.f1_app.f1.data.getCircuitWinners
import com.anpurnama.f1_app.f1.model.CircuitMostWins
import com.anpurnama.f1_app.f1.model.MostWinningDriver
import com.anpurnama.f1_app.f1.model.MostWinningTeam
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * All-time most-winning driver + team at a circuit. Sourced from
 * jolpica `/circuits/{id}/results/1.json` (P1 per race, client-aggregated).
 * One call per circuit-detail open, ~25KB worst-case payload, server
 * `max-age=3600` cached. Ktor HttpCache covers re-opens.
 *
 * **ID translation:** the private 5-entry map lives in `F1Api.kt` and
 * is applied at the network extension; the public `circuitId` here is
 * f1api.dev's form everywhere else in the app.
 *
 * **Aggregation:** two `groupingBy { }.eachCount()` walks over the
 * P1 rows, O(n) on races-at-circuit (n ≤ 76 for any current F1
 * circuit). Ties resolve to the **first** leader encountered in
 * iteration order (Kotlin's `groupingBy` returns insertion-ordered
 * `eachCount`; `maxByOrNull` keeps the first on ties). Ties are
 * expected only at the very top of a few circuits (e.g. Hamilton /
 * Schumacher 5 wins each at Monza); the screen renders the first
 * one jolpica reports and the design accepts that.
 *
 * **Domain-purity:** only the `HttpClient` (injected by Wiring)
 * crosses the `android.*` boundary.
 */
class GetCircuitMostWinsUseCase(private val client: HttpClient) {
    suspend operator fun invoke(
        f1apiCircuitId: String,
        forceRefresh: Boolean = false,
    ): Outcome<CircuitMostWins> = try {
        val dto = client.getCircuitWinners(f1apiCircuitId, forceRefresh = forceRefresh)
        Outcome.Success(dto.toCircuitMostWins())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

/**
 * DTO → domain mapper. Walks the per-race P1 rows, groups by
 * `driverId` and `constructorId` separately, and returns the
 * leader of each. `topDriver` and `topTeam` are independently
 * nullable: a circuit with P1 rows but no resolvable driver
 * (e.g. jolpica returns a row with `driverId = null` — the
 * pre-1950 era has a few of these) still reports a top team.
 *
 * The leader's name comes from the same P1 row that contributed
 * the winning count, so one walk per axis is enough.
 *
 * Visible for testing (internal); not exposed as public API.
 */
internal fun CircuitWinnersResponseDto.toCircuitMostWins(): CircuitMostWins {
    val p1Rows = mrData.raceTable.races.mapNotNull { race ->
        race.results.firstOrNull { it.position == "1" }
    }

    val topDriver = p1Rows.topLeader(
        idOf = { it.driver.driverId },
        nameOf = { row -> joinName(row.driver.givenName, row.driver.familyName, fallback = row.driver.driverId) },
        toModel = { id, name, wins -> MostWinningDriver(driverId = id, name = name, wins = wins) },
    )

    val topTeam = p1Rows.topLeader(
        idOf = { it.constructor.constructorId },
        nameOf = { row -> row.constructor.name?.takeIf(String::isNotBlank) ?: (row.constructor.constructorId ?: "") },
        toModel = { id, name, wins -> MostWinningTeam(teamId = id, name = name, wins = wins) },
    )

    return CircuitMostWins(
        topDriver = topDriver,
        topTeam = topTeam,
        totalRaces = p1Rows.size,
    )
}

/**
 * Generic one-pass aggregation. Filters to rows with a usable id,
 * groups by id, picks the leader, and pulls the leader's name
 * from the **first** row that contributed the leader's id (ties
 * resolve to insertion order, matching `groupingBy` semantics).
 */
private inline fun <T, R> List<T>.topLeader(
    idOf: (T) -> String?,
    nameOf: (T) -> String,
    toModel: (id: String, name: String, wins: Int) -> R,
): R? {
    val rows = mapNotNull { row ->
        val id = idOf(row)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        id to row
    }
    val counts = rows.groupingBy { it.first }.eachCount()
    val winnerId = counts.maxByOrNull { it.value }?.key ?: return null
    val winnerName = rows.first { it.first == winnerId }.let { (id, row) -> nameOf(row).ifBlank { id } }
    return toModel(winnerId, winnerName, counts.getValue(winnerId))
}

private fun joinName(given: String?, family: String?, fallback: String?): String {
    val parts = listOfNotNull(given, family).filter { it.isNotBlank() }
    return parts.joinToString(" ").ifBlank { fallback?.takeIf { it.isNotBlank() } ?: "" }
}
