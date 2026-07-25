package com.anpurnama.f1_app.f1.model

/**
 * All-time circuit detail. Built by `GetCircuitUseCase` from f1api.dev
 * `/circuits/{circuitId}`. Distinct from the lighter [Circuit] carried on
 * every race row: the inlined `Circuit` only has id / name / length / corners
 * / city / country, while the detail page also needs the all-time lap record
 * and its attribution, plus the first-GP year.
 *
 * `circuitLengthKm` is the **decimal** km (the wire `circuitLength: Int` is
 * in meters; e.g. Bahrain 5412 → 5.412 km). The `Number` form is `Double`
 * to match the rest of the season-aggregates code path.
 *
 * `lapRecord` carries the wire format as-is (`"1:21:046"`, no leading zero
 * on the minute) — the screen formats the cell. `fastestLapDriverId` /
 * `fastestLapTeamId` / `fastestLapYear` are nullable because the API may
 * not have an all-time race lap for a brand-new circuit (post-2023 venues
 * that haven't yet hosted enough races for a record). `null` triple
 * means "no record" and the screen renders the cell accordingly.
 *
 * **Domain-purity:** pure Kotlin, zero `android.*` imports.
 */
data class CircuitDetail(
    val id: String,
    val name: String,
    val country: String?,
    val city: String?,
    val circuitLengthKm: Double,
    val numberOfCorners: Int?,
    val firstParticipationYear: Int?,
    val lapRecord: LapRecord?,
)

/**
 * All-time race-lap record at a circuit. `time` is the wire format from
 * f1api.dev (`"1:21:046"`, a 3-tuple `MM:SS:mmm`); the screen formats the
 * colon-separated pair for display. Driver and team ids match f1api.dev's
 * namespace and can resolve to DriverDetail / TeamDetail — but a circuit's
 * all-time record is frequently held by a retired driver, so the screen
 * shows the record as read-only data (no driver link by default).
 */
data class LapRecord(
    val time: String,
    val driverId: String,
    val teamId: String,
    val year: Int,
)

/**
 * All-time most-winning driver + team at a circuit. Built by
 * `GetCircuitMostWinsUseCase` from jolpica
 * `/circuits/{id}/results/1.json` (P1 per race, client-aggregated).
 * `totalRaces` is the count of races aggregated over (n ≤ 76 for any
 * current F1 circuit).
 *
 * `topDriver` / `topTeam` can independently be `null` when no P1 rows
 * exist for the circuit (a brand-new circuit, a 404 against jolpica, or
 * the f1api.dev id is not in jolpica's namespace at all). The screen
 * renders the per-cell empty state from the lode UX family — one
 * missing leader never blanks the other.
 */
data class CircuitMostWins(
    val topDriver: MostWinningDriver?,
    val topTeam: MostWinningTeam?,
    val totalRaces: Int,
)

/**
 * Single driver's all-time record at a circuit. `name` is the rendered
 * form (`givenName + " " + familyName`); the screen shows it as
 * `<name> — <wins> wins`. `wins` is the count of races this driver
 * won the P1 slot at this circuit (1+).
 */
data class MostWinningDriver(
    val driverId: String,
    val name: String,
    val wins: Int,
)

/**
 * Single team's all-time record at a circuit. `wins` is the count of
 * races this constructor's car won the P1 slot at this circuit (1+).
 */
data class MostWinningTeam(
    val teamId: String,
    val name: String,
    val wins: Int,
)
