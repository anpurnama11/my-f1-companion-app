package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.model.SessionResult
import com.anpurnama.f1_app.f1.model.SessionType
import io.ktor.client.HttpClient

/**
 * Per-round free-practice results from Jolpica alpha
 * `/f1/alpha/results/{round_id}/{FP1|FP2|FP3}/` — fetched through the shared
 * [loadAlpha] loader, the same path the sprint and sprint-qualifying use cases
 * use. Drives the SessionResult FP1/FP2/FP3 screens via [GetSessionResultUseCase].
 *
 * The Jolpica alpha filter for a practice session is the session's own label
 * (`"FP1"`/`"FP2"`/`"FP3"`). There is no local session-type guard: a non-practice
 * session reaching here resolves to a filter outside the alpha require set, and
 * [loadAlpha] maps that to the not-scheduled Outcome — matching the sprint use
 * cases, whose guard also lives entirely in the alpha layer.
 *
 * Pure Kotlin: only the `HttpClient` (injected by Wiring) crosses the
 * android.* boundary.
 */
class GetPracticeResultUseCase(private val client: HttpClient) {
    suspend operator fun invoke(
        year: Int,
        round: Int,
        session: SessionType,
        forceRefresh: Boolean = false,
    ): Outcome<SessionResult> {
        val filter = when (session) {
            SessionType.FP1 -> "FP1"
            SessionType.FP2 -> "FP2"
            SessionType.FP3 -> "FP3"
            // Non-practice session → alpha require rejects → loadAlpha maps to
            // the not-scheduled "Session is unavailable" outcome.
            else -> session.shortLabel
        }
        return loadAlpha(client, year, round, filter, session, forceRefresh)
    }
}