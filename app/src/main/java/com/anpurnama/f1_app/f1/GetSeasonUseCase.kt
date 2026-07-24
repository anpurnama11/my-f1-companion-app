package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.SeasonResponseDto
import com.anpurnama.f1_app.f1.data.getCurrent
import com.anpurnama.f1_app.f1.data.getSeason
import com.anpurnama.f1_app.f1.model.Circuit
import com.anpurnama.f1_app.f1.model.Race
import com.anpurnama.f1_app.f1.model.RaceSchedule
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.f1.model.SessionSlot
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException

/**
 * Returns the current season (full schedule + pre-computed aggregates).
 *
 * `forceRefresh = true` adds a `Cache-Control: no-cache` header so the
 * request bypasses the HttpCache. Used by the Homepage pull-to-refresh.
 *
 * Pure Kotlin: only the `HttpClient` (injected by Wiring) crosses the
 * android.* boundary.
 */
class GetSeasonUseCase(private val client: HttpClient) {
    suspend operator fun invoke(forceRefresh: Boolean = false): Outcome<Season> = try {
        val dto = client.getCurrent(forceRefresh = forceRefresh)
        Outcome.Success(dto.toSeason())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }

    suspend operator fun invoke(year: Int, forceRefresh: Boolean = false): Outcome<Season> = try {
        Outcome.Success(client.getSeason(year, forceRefresh).toSeason())
    } catch (e: ClientRequestException) {
        Outcome.Failure("Request failed (${e.response.status.value})")
    } catch (e: ServerResponseException) {
        Outcome.Failure("Server error (${e.response.status.value})")
    } catch (e: Exception) {
        Outcome.Failure(e.message ?: "Network error")
    }
}

/**
 * DTO → domain mapping. Aggregates are pre-computed here so the
 * ViewModel never re-walks the list.
 *
 * **Unit on the wire:** `circuitLength` arrives as a string with the
 * **meter** value concatenated to `"km"` (e.g. Bahrain `"5412km"` =
 * 5.412 km, not 5412 km). The mapper digit-strips then divides by
 * 1000 so `totalKm` is in real kilometers. `Double` is required to
 * preserve the per-circuit precision across a season sum; a 24-race
 * season of mixed-length circuits needs three decimal places.
 *
 * Visible for testing (internal); not exposed as public API.
 */
internal fun SeasonResponseDto.toSeason(): Season {
    val completed = races.filter { it.winner != null }
    val totalKm = completed.sumOf { race ->
        race.circuit.circuitLength.filter(Char::isDigit).toIntOrNull()?.div(1000.0) ?: 0.0
    }
    val totalLaps = completed.sumOf { it.laps ?: 0 }
    val progress = if (races.isEmpty()) {
        0f
    } else {
        completed.size.toFloat() / races.size
    }
    return Season(
        year = season,
        races = races.map { it.toRace() },
        completedGp = completed.size,
        totalKm = totalKm,
        totalLaps = totalLaps,
        progressPercent = progress,
    )
}

private fun com.anpurnama.f1_app.f1.data.RaceDto.toRace(): Race = Race(
    round = round,
    name = raceName.orEmpty(),
    circuit = Circuit(
        id = circuit.circuitId,
        name = circuit.circuitName.orEmpty(),
        circuitLengthRaw = circuit.circuitLength,
        corners = circuit.corners,
        city = circuit.city,
        country = circuit.country,
    ),
    winnerId = winner?.driverId,
    laps = laps,
    schedule = schedule.toSchedule(),
)

/**
 * Maps the f1api.dev `/current` `RaceScheduleDto` to the domain
 * [RaceSchedule]. The API may omit slots or send semantically empty
 * objects (`{ "date": null, "time": null }`); both forms normalize to
 * null. Returns null when every slot is absent so the screen can render
 * "no schedule" instead of a row of empty cells.
 */
private fun com.anpurnama.f1_app.f1.data.RaceScheduleDto.toSchedule(): RaceSchedule? {
    val normalized = RaceSchedule(
        fp1 = fp1?.toSlotOrNull(),
        fp2 = fp2?.toSlotOrNull(),
        fp3 = fp3?.toSlotOrNull(),
        sprintQualy = sprintQualy?.toSlotOrNull(),
        sprintRace = sprintRace?.toSlotOrNull(),
        qualy = qualy?.toSlotOrNull(),
        race = race?.toSlotOrNull(),
    )
    if (normalized.fp1 == null && normalized.fp2 == null && normalized.fp3 == null &&
        normalized.sprintQualy == null && normalized.sprintRace == null &&
        normalized.qualy == null && normalized.race == null
    ) return null
    return normalized
}

private fun com.anpurnama.f1_app.f1.data.SessionDto.toSlotOrNull(): SessionSlot? {
    if (date.isNullOrBlank() && time.isNullOrBlank()) return null
    return toSlot()
}

private fun com.anpurnama.f1_app.f1.data.SessionDto.toSlot(): SessionSlot =
    SessionSlot(date = date, time = time)
