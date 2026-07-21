package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.F1API_TO_OPENF1_COUNTRY
import com.anpurnama.f1_app.f1.data.OpenF1SessionDto
import com.anpurnama.f1_app.f1.data.getOpenF1Sessions
import com.anpurnama.f1_app.f1.model.SessionTime
import com.anpurnama.f1_app.f1.model.WeekendSchedule
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.datetime.Instant

/**
 * Race-weekend schedule (FP1, FP2, FP3, Qualifying, Sprint, Race …) for
 * the next race, sourced from OpenF1 `/v1/sessions`. Drives the Homepage
 * §1 countdown card — replaces the previous "next GP name + date" card
 * with a list of every weekend session and a live countdown to the next
 * upcoming one.
 *
 * Pipeline:
 *  1. `GET /v1/sessions?year=…&country_name=…` (no `session_name` filter
 *     — we want every session in the weekend).
 *  2. If the literal returns 0 sessions, retry with
 *     `F1API_TO_OPENF1_COUNTRY[country]` (the 1-entry Silverstone fix:
 *     f1api.dev "Great Britain" → OpenF1 "United Kingdom").
 *  3. Map each `OpenF1SessionDto` to `SessionTime` (parse `date_start`,
 *     label the session, drop cancelled), sort by `start` ascending.
 *
 * Returns:
 *  - `Success(WeekendSchedule)` on a resolvable schedule.
 *  - `Success(null)` on no resolvable sessions (pre-2023, off-season, both
 *    country lookups returning 0). The §1 card renders an empty state
 *    — "no data" is the truth; never a fake "—".
 *  - `Failure` on 4xx/5xx.
 *
 * Pure Kotlin: only the `HttpClient` (injected by Wiring) crosses the
 * android.* boundary. No `forceRefresh` — OpenF1 sends no cache headers
 * and the Ktor `HttpCache` plugin is bypassed (per `F1Api.kt`); the next
 * `getOpenF1Sessions` call always re-fetches.
 */
class GetRaceWeekendScheduleUseCase(private val client: HttpClient) {

    suspend operator fun invoke(
        year: Int,
        country: String,
    ): Outcome<WeekendSchedule?> = try {
        val raw = client.getOpenF1Sessions(year, country).ifEmpty {
            F1API_TO_OPENF1_COUNTRY[country]
                ?.let { client.getOpenF1Sessions(year, it) }
                ?: emptyList()
        }
        val sessions = raw.mapNotNull { it.toSessionTime() }.sortedBy { it.start }
        val schedule = WeekendSchedule(sessions).takeIf { it.sessions.isNotEmpty() }
        Outcome.Success(schedule)
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

/**
 * DTO → domain mapping. `internal` so the test can reach it from the
 * same module, but it stays off the public API surface. Per the existing
 * convention from ticket 01.
 *
 * - Cancelled sessions are dropped (OpenF1 sets `is_cancelled = true`).
 * - Unparseable `dateStart` is dropped (the schedule still surfaces the
 *   well-formed sessions; this is the safe partial-success path).
 * - Unknown `sessionName` falls back to the raw string so a future OpenF1
 *   session type doesn't blank the card; the short label is the first 4
 *   characters uppercased.
 */
internal fun OpenF1SessionDto.toSessionTime(): SessionTime? {
    if (isCancelled) return null
    val start = dateStart
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: return null
    val (label, short) = when (sessionName) {
        "Practice 1" -> "Practice 1" to "FP1"
        "Practice 2" -> "Practice 2" to "FP2"
        "Practice 3" -> "Practice 3" to "FP3"
        "Sprint Qualifying" -> "Sprint Qualifying" to "SQ"
        "Sprint" -> "Sprint" to "SPRINT"
        "Qualifying" -> "Qualifying" to "QUALI"
        "Race" -> "Race" to "RACE"
        else -> sessionName.orEmpty() to (sessionName?.take(4)?.uppercase() ?: "?")
    }
    return SessionTime(label, short, start)
}
