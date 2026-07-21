package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.F1API_TO_OPENF1_COUNTRY
import com.anpurnama.f1_app.f1.data.getOpenF1Meetings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Circuit track-layout image URL from OpenF1 `/v1/meetings`. Drives the
 * decorative circuit graphic on the Homepage §1 countdown card.
 *
 * Pipeline:
 *  1. `GET /v1/meetings?year=…&country_name=…`.
 *  2. If the literal returns 0 meetings, retry with
 *     `F1API_TO_OPENF1_COUNTRY[country]` (Silverstone fallback).
 *  3. Return the first non-null `circuit_image` URL.
 *
 * Returns:
 *  - `Success(imageUrl)` when an image is found.
 *  - `Success(null)` when no meeting or no image is resolvable (pre-2023,
 *    off-season, both country lookups returning 0). The card renders
 *    without the decorative image.
 *  - `Failure` on 4xx/5xx.
 */
class GetCircuitImageUseCase(private val client: HttpClient) {

    suspend operator fun invoke(
        year: Int,
        country: String,
    ): Outcome<String?> = try {
        val meetings = client.getOpenF1Meetings(year, country).ifEmpty {
            F1API_TO_OPENF1_COUNTRY[country]
                ?.let { client.getOpenF1Meetings(year, it) }
                ?: emptyList()
        }
        val imageUrl = meetings.firstNotNullOfOrNull { it.circuitImage }
        Outcome.Success(imageUrl)
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}
