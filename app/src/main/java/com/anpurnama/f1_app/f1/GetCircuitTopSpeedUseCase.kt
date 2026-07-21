package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.F1API_TO_OPENF1_COUNTRY
import com.anpurnama.f1_app.f1.data.getOpenF1Laps
import com.anpurnama.f1_app.f1.data.getOpenF1Sessions
import com.anpurnama.f1_app.f1.model.TopSpeed
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Top speed (km/h) for a circuit on a given race weekend. Drives Homepage
 * §3 ("Top speed" cell) and (later) the Round detail.
 *
 * Pipeline:
 *  1. `GET /v1/sessions?year=…&country_name=…&session_name=Qualifying`
 *  2. Filter to the session whose `date_start` date matches
 *     f1api.dev's `schedule.qualy.date` (the Qualifying day). The original
 *     ticket 11 research claimed `race.date` matched, but live probes show
 *     OpenF1's Qualifying is on the day before the race (or two days
 *     before for sprint weekends). `qualy.date` is the right join key.
 *     `country_name` alone is insufficient for multi-circuit countries
 *     (US 3 circuits, Italy 2, Spain 2 from 2026); the date match is the
 *     unique disambiguator.
 *  3. If literal returns 0 sessions, retry with
 *     `F1API_TO_OPENF1_COUNTRY[country]` (the 1-entry Silverstone fix:
 *     f1api.dev "Great Britain" → OpenF1 "United Kingdom").
 *  4. `GET /v1/laps?session_key=…`, take `max(lap.stSpeed)`.
 *
 * Returns:
 *  - `Success(TopSpeed)` on a resolvable session_key + non-null speeds.
 *  - `Success(null)` on no resolvable session (pre-2023, off-calendar,
 *    both country lookups returning 0, all-laps-null). §3 renders an
 *    empty cell — "no data" is the truth; never a fake "—".
 *  - `Failure` on 4xx/5xx. The use-case is the one place that can
 *    reach this surface, so the failure mode is rare in practice.
 */
class GetCircuitTopSpeedUseCase(private val client: HttpClient) {

    /**
     * @param circuitId   the f1api.dev circuitId (e.g. "hungaroring") —
     *                    surfaced on the `TopSpeed` model so the §3 cell
     *                    can navigate to `CircuitDetail(circuitId)`.
     * @param country     the f1api.dev `circuit.country` value (e.g.
     *                    "Hungary"); the OpenF1 `country_name` filter.
     * @param year        the season year.
     * @param qualyDate   f1api.dev's `schedule.qualy.date` (Qualifying
     *                    day, "YYYY-MM-DD"). The OpenF1 join date.
     */
    suspend operator fun invoke(
        circuitId: String,
        country: String,
        year: Int,
        qualyDate: String,
    ): Outcome<TopSpeed?> = try {
        val key = sessionKeyFor(country, year, qualyDate)
            ?: F1API_TO_OPENF1_COUNTRY[country]?.let { sessionKeyFor(it, year, qualyDate) }
        if (key == null) {
            Outcome.Success(null)
        } else {
            val laps = client.getOpenF1Laps(key)
            val peak = laps.mapNotNull { it.stSpeed }.maxOrNull()
            if (peak == null) Outcome.Success(null) else Outcome.Success(TopSpeed(circuitId, peak))
        }
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }

    /**
     * Filter the year's Qualifying sessions for the given country to the
     * one whose `date_start` date matches the f1api.dev Qualifying date.
     * Returns the session_key, or null if the race isn't on the OpenF1
     * calendar (pre-2023, cancelled weekend, etc.).
     */
    private suspend fun sessionKeyFor(country: String, year: Int, qualyDate: String): Int? {
        val sessions = client.getOpenF1Sessions(year, country, "Qualifying")
        return sessions
            .firstOrNull { it.dateStart?.toDateOnly() == qualyDate }
            ?.sessionKey
    }
}

/**
 * OpenF1 returns `date_start` as ISO-8601 with time (e.g.
 * `"2024-07-06T14:00:00+00:00"`); we want the date portion
 * ("2024-07-06") to compare against f1api.dev's `schedule.qualy.date`.
 * The substring slice is enough; if the string is malformed the
 * equality check is a no-op and the lookup returns null, which is the
 * right answer (caller surfaces an empty cell).
 *
 * ponytail: don't pull in `kotlinx-datetime` for this one slice; the
 * inline `Char` check is 4 lines and the test covers it.
 */
private fun String.toDateOnly(): String? {
    val dash1 = indexOf('-')
    if (dash1 != 4) return null
    val dash2 = indexOf('-', dash1 + 1)
    if (dash2 != 7) return null
    if (length < 10 || this[10] != 'T') return null
    return substring(0, 10)
}
