package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.model.RoundResult

/**
 * Past-list podium: the top-3 finishing drivers of a single round.
 * **Composes [GetRoundResultsUseCase]** and slices `results[0..2]`
 * from the full grid — no extra network call, per the ticket 03
 * spec and `lode/wayfinder/f1app/past-list.md`.
 *
 * HttpCache is the only reason the Past list and the Round detail
 * drilldown don't double-fetch the same round: when a row's podium
 * has been fetched, opening that row in the drilldown is a cache hit
 * on the same `/{year}/{round}/race` endpoint.
 *
 * **Partial podium on short grids:** when the source has fewer than
 * 3 finishers (e.g. an early-season 2-car test or a heavily-redacted
 * mock), the use case returns what it has — a 1- or 2-entry
 * `topThree`, never a fake "—" and never a failure.
 *
 * **Empty results → Failure:** when the source has zero finishers
 * (empty `results` array), the use case returns `Failure("No
 * results")` rather than `Success(emptyList())`. The screen's
 * `PodiumCell` shows the retry row instead of a silent blank cell.
 *
 * Pure Kotlin: only the [GetRoundResultsUseCase] (injected by
 * Wiring) crosses the network boundary.
 */
class GetRoundPodiumUseCase(private val getRoundResults: GetRoundResultsUseCase) {
    suspend operator fun invoke(
        year: Int,
        round: Int,
        forceRefresh: Boolean = false,
    ): Outcome<RoundPodium> = when (val out = getRoundResults(year, round, forceRefresh)) {
        is Outcome.Success -> {
            val topThree = out.data.results.take(RoundPodium.PODIUM_SIZE)
            if (topThree.isEmpty()) Outcome.Failure("No results")
            else Outcome.Success(RoundPodium(topThree = topThree))
        }
        is Outcome.Failure -> out
        is Outcome.Loading -> out
    }
}

/**
 * The top-3 finishing drivers of a single round, in finishing
 * position order. `topThree` is exactly 3 entries for a normal
 * full-grid race; may be 1 or 2 on a short grid (partial podium).
 */
data class RoundPodium(
    val topThree: List<RoundResult>,
) {
    companion object {
        const val PODIUM_SIZE = 3
    }
}
