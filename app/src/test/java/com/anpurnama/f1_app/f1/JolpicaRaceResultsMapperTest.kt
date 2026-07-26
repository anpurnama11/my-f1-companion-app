package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.JolpicaRaceResultsResponseDto
import com.anpurnama.f1_app.f1.toRoundResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rung 1 mapper test: Jolpica standard `/ergast/f1/{year}/{round}/results.json`
 * → domain [com.anpurnama.f1_app.f1.model.RoundResults]. Pure mapping, no IO.
 *
 * The standard envelope carries the full Ergast richness that the old f1api.dev
 * `/{year}/{round}/race` hybrid merge relied on separate calls for:
 * `status`, numeric `grid`, `points`, `Time.time`, per-row `FastestLap.Time.time`,
 * `Constructor`, `Circuit.Location`, and the `Driver`'s `givenName`/`familyName`/
 * `code`/`permanentNumber`. Verify the load-bearing fields end-to-end against a
 * finisher, a lapped row, and a retiree (non-classified `positionText` → "NC").
 *
 * The mapper is `internal` so the test reaches it from the same module.
 */
class JolpicaRaceResultsMapperTest {

    private fun envelope(race: JolpicaRaceResultsResponseDto.RaceDto) =
        JolpicaRaceResultsResponseDto(
            mrData = JolpicaRaceResultsResponseDto.MrDataDto(
                raceTable = JolpicaRaceResultsResponseDto.RaceTableDto(
                    season = "2024",
                    round = "1",
                    races = listOf(race),
                ),
            ),
        )

    private fun driver(
        id: String,
        given: String? = null,
        family: String? = null,
        code: String? = null,
        permanentNumber: String? = null,
    ) = JolpicaRaceResultsResponseDto.DriverDto(
        driverId = id, givenName = given, familyName = family,
        code = code, permanentNumber = permanentNumber,
    )

    @Test
    fun `toRoundResults maps the full Ergast envelope into the domain model`() {
        val dto = envelope(
            JolpicaRaceResultsResponseDto.RaceDto(
                season = "2024",
                round = "1",
                raceName = "Gulf Air Bahrain Grand Prix 2024",
                date = "2024-03-02",
                time = "15:00:00Z",
                circuit = JolpicaRaceResultsResponseDto.CircuitDto(
                    circuitId = "bahrain",
                    circuitName = "Bahrain International Circuit",
                    location = JolpicaRaceResultsResponseDto.LocationDto(
                        locality = "Sakhir",
                        country = "Bahrain",
                    ),
                ),
                results = listOf(
                    // Winner — finisher, fastest-lap holder.
                    JolpicaRaceResultsResponseDto.ResultDto(
                        number = "1", position = "1", positionText = "1", points = "25",
                        grid = "1", laps = "57", status = "Finished",
                        driver = driver("max_verstappen", "Max", "Verstappen", "VER", "1"),
                        constructor = JolpicaRaceResultsResponseDto.ConstructorDto(
                            constructorId = "red_bull", name = "Red Bull Racing",
                        ),
                        time = JolpicaRaceResultsResponseDto.TimeDto(
                            millis = "5500000", time = "1:31:44.000",
                        ),
                        fastestLap = JolpicaRaceResultsResponseDto.FastestLapDto(
                            rank = "1", lap = "57",
                            time = JolpicaRaceResultsResponseDto.TimeDto(time = "1:32.000"),
                        ),
                    ),
                    // P2 — lapped finisher with a gap time.
                    JolpicaRaceResultsResponseDto.ResultDto(
                        number = "11", position = "2", positionText = "2", points = "18",
                        grid = "5", laps = "56", status = "Lapped",
                        driver = driver("perez", "Sergio", "Pérez", "PER", "11"),
                        constructor = JolpicaRaceResultsResponseDto.ConstructorDto(
                            constructorId = "red_bull", name = "Red Bull Racing",
                        ),
                        time = JolpicaRaceResultsResponseDto.TimeDto(time = "+1 lap"),
                        fastestLap = JolpicaRaceResultsResponseDto.FastestLapDto(
                            rank = "3", lap = "54",
                            time = JolpicaRaceResultsResponseDto.TimeDto(time = "1:32.500"),
                        ),
                    ),
                    // Retiree — positionText "R" normalizes to "NC", no Time, no FastestLap.
                    JolpicaRaceResultsResponseDto.ResultDto(
                        number = "44", position = "19", positionText = "R", points = "0",
                        grid = "11", laps = "15", status = "Retired",
                        driver = driver("hamilton", "Lewis", "Hamilton", "HAM", "44"),
                        constructor = JolpicaRaceResultsResponseDto.ConstructorDto(
                            constructorId = "ferrari", name = "Ferrari",
                        ),
                        time = null,
                        fastestLap = null,
                    ),
                ),
            ),
        )

        val out = dto.toRoundResults()

        assertEquals(2024, out.year)
        assertEquals(1, out.round)
        assertEquals("Gulf Air Bahrain Grand Prix 2024", out.raceName)
        assertEquals("2024-03-02", out.date)
        assertEquals("15:00:00Z", out.time)
        assertEquals("bahrain", out.circuit.id)
        assertEquals("Bahrain International Circuit", out.circuit.name)
        assertEquals("Sakhir", out.circuit.city)
        assertEquals("Bahrain", out.circuit.country)
        // Standard envelope has no length/corners — left blank (degraded fallback).
        assertEquals("", out.circuit.circuitLengthRaw)
        assertNull(out.circuit.corners)
        assertEquals(3, out.results.size)

        val winner = out.results[0]
        assertEquals("1", winner.position)
        assertEquals(25, winner.points)
        assertEquals("1", winner.grid)
        assertEquals("1:31:44.000", winner.time)
        assertEquals("1:32.000", winner.fastLap)
        assertEquals("max_verstappen", winner.driverId)
        assertEquals("Max Verstappen", winner.driverName)
        assertEquals("VER", winner.driverShortName)
        assertEquals(1, winner.driverNumber)
        assertEquals("red_bull", winner.teamId)
        assertEquals("Red Bull Racing", winner.teamName)
        assertEquals("Finished", winner.status)

        val lapped = out.results[1]
        assertEquals("2", lapped.position)
        assertEquals(18, lapped.points)
        assertEquals("Lapped", lapped.status)
        assertEquals("+1 lap", lapped.time)
        assertEquals("1:32.500", lapped.fastLap)

        val retiree = out.results[2]
        assertEquals("NC", retiree.position)
        assertEquals(0, retiree.points)
        assertEquals("11", retiree.grid)
        assertEquals("Retired", retiree.status)
        assertNull(retiree.time)
        assertNull(retiree.fastLap)
    }

    @Test
    fun `empty Races array yields empty results and a default circuit`() {
        val dto = JolpicaRaceResultsResponseDto(
            mrData = JolpicaRaceResultsResponseDto.MrDataDto(
                raceTable = JolpicaRaceResultsResponseDto.RaceTableDto(
                    season = "2024",
                    round = "7",
                    races = emptyList(),
                ),
            ),
        )

        val out = dto.toRoundResults()
        assertEquals(2024, out.year)
        assertEquals(7, out.round)
        assertEquals("", out.raceName)
        assertNull(out.date)
        assertEquals("", out.circuit.id)
        assertEquals("", out.circuit.name)
        assertEquals(0, out.results.size)
    }

    @Test
    fun `driver falls back to driverId when given and family name are absent`() {
        val dto = envelope(
            JolpicaRaceResultsResponseDto.RaceDto(
                raceName = "Bahrain GP",
                results = listOf(
                    JolpicaRaceResultsResponseDto.ResultDto(
                        number = "27", position = "9", positionText = "9", points = "2",
                        grid = "16", status = "Finished",
                        driver = JolpicaRaceResultsResponseDto.DriverDto(
                            driverId = "hulkenberg", permanentNumber = "27",
                        ),
                        constructor = JolpicaRaceResultsResponseDto.ConstructorDto(
                            constructorId = "haas", name = "Haas F1 Team",
                        ),
                    ),
                ),
            ),
        )

        val row = dto.toRoundResults().results.single()
        assertEquals("hulkenberg", row.driverName)
        assertEquals("hulkenberg", row.driverId)
        assertEquals(27, row.driverNumber)
        assertNull(row.driverShortName)
    }
}