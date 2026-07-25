package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.CircuitDetailResponseDto
import com.anpurnama.f1_app.f1.data.getCircuit
import com.anpurnama.f1_app.f1.model.CircuitDetail
import com.anpurnama.f1_app.f1.model.LapRecord
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * All-time circuit metadata from f1api.dev `/circuits/{circuitId}`.
 * Drives the [com.anpurnama.f1_app.feature.round.RoundScreen]'s tap
 * through to the `CircuitDetail` page, where it sits alongside the
 * jolpica-sourced most-wins aggregation.
 *
 * **404 handling:** the f1api.dev endpoint returns 404 for an unknown
 * `circuitId`. The use case surfaces that as `Outcome.Failure`; the
 * screen renders the empty state via the shared UX family (no
 * placeholder, no fake data).
 *
 * `forceRefresh = true` adds `Cache-Control: no-cache` so the request
 * bypasses the HttpCache. Used by the screen's pull-to-refresh.
 *
 * **Domain-purity:** only the `HttpClient` (injected by Wiring)
 * crosses the `android.*` boundary.
 */
class GetCircuitUseCase(private val client: HttpClient) {
    suspend operator fun invoke(
        f1apiCircuitId: String,
        forceRefresh: Boolean = false,
    ): Outcome<CircuitDetail> = try {
        val dto = client.getCircuit(f1apiCircuitId, forceRefresh = forceRefresh)
        val mapped = dto.toCircuitDetail()
            ?: return Outcome.Failure("Circuit $f1apiCircuitId not found")
        Outcome.Success(mapped)
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

/**
 * DTO → domain mapper. The endpoint returns a one-element
 * `circuit: [...]` array; an empty array means "no such circuit id" and
 * becomes `null` at the seam (404 is also handled by the surrounding
 * use case, but the empty-list case is the wire shape for some 200
 * responses and must not become a fake "Bahrain" success).
 *
 * **Unit on the wire:** `circuitLength` arrives as an `Int` in
 * **meters** (Bahrain 5412 → 5.412 km) — distinct from the
 * `"<N>km"` String form used on `/current*` envelopes. Divide by 1000
 * to keep the rest of the season-aggregates code path consistent on
 * `Double` kilometers.
 *
 * `lapRecord` and its three attribution fields are all nullable at
 * the wire; the model requires the four together (a record without
 * attribution is meaningless), so a partial set collapses to `null`
 * at the seam.
 *
 * Visible for testing (internal); not exposed as public API.
 */
internal fun CircuitDetailResponseDto.toCircuitDetail(): CircuitDetail? {
    val circuit = circuit.firstOrNull() ?: return null
    val lapRecord = buildLapRecord(circuit)
    return CircuitDetail(
        id = circuit.circuitId,
        name = circuit.circuitName.orEmpty(),
        country = circuit.country,
        city = circuit.city,
        circuitLengthKm = circuit.circuitLength / 1000.0,
        numberOfCorners = circuit.numberOfCorners,
        firstParticipationYear = circuit.firstParticipationYear,
        lapRecord = lapRecord,
    )
}

private fun buildLapRecord(c: CircuitDetailResponseDto.CircuitDetailDto): LapRecord? {
    val time = c.lapRecord?.takeIf { it.isNotBlank() } ?: return null
    val driverId = c.fastestLapDriverId?.takeIf { it.isNotBlank() } ?: return null
    val teamId = c.fastestLapTeamId?.takeIf { it.isNotBlank() } ?: return null
    val year = c.fastestLapYear ?: return null
    return LapRecord(time = time, driverId = driverId, teamId = teamId, year = year)
}
