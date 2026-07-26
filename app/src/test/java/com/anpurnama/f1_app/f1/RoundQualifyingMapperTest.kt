package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.JolpicaQualifyingResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 1 mapper test: Jolpica standard `/ergast/f1/{year}/{round}/qualifying.json`
 * Ergast DTO → domain. Pure mapping, no IO.
 *
 * The Ergast qualifying envelope:
 *  - `raceTable.races` is a one-element array for the requested round (empty
 *    for a future/not-yet-run round).
 *  - `QualifyingResults` is ordered by qualifying `position` (1-based),
 *    which maps to the existing `QualifyingResult.gridPosition` Int contract.
 *  - `Q1`/`Q2`/`Q3` are dirty lap-time Strings or null when the driver was
 *    knocked out before that segment (Q1-only knockout has Q2/Q3 = null).
 *  - Every row carries a `Constructor` (constructorId/name) and full `Driver`
 *    richness (givenName/familyName/code/permanentNumber), confirming the
 *    full-richness mapping the live probe established.
 */
class RoundQualifyingMapperTest {

    @Test
    fun `toRoundQualifying maps the Ergast qualifying envelope into the domain model`() {
        val dto = JolpicaQualifyingResponseDto(
            mrData = JolpicaQualifyingResponseDto.MrDataDto(
                raceTable = JolpicaQualifyingResponseDto.RaceTableDto(
                    season = "2024",
                    round = "1",
                    races = listOf(
                        JolpicaQualifyingResponseDto.RaceDto(
                            season = "2024",
                            round = "1",
                            raceName = "Gulf Air Bahrain Grand Prix 2024",
                            date = "2024-03-02",
                            time = "15:00:00Z",
                            circuit = JolpicaQualifyingResponseDto.CircuitDto(
                                circuitId = "bahrain",
                                circuitName = "Bahrain International Circuit",
                                location = JolpicaQualifyingResponseDto.LocationDto(
                                    locality = "Sakhir",
                                    country = "Bahrain",
                                ),
                            ),
                            qualifyingResults = listOf(
                                JolpicaQualifyingResponseDto.QualifyingResultDto(
                                    number = "1",
                                    position = "1",
                                    driver = JolpicaQualifyingResponseDto.DriverDto(
                                        driverId = "max_verstappen",
                                        permanentNumber = "1",
                                        code = "VER",
                                        givenName = "Max",
                                        familyName = "Verstappen",
                                    ),
                                    constructor = JolpicaQualifyingResponseDto.ConstructorDto(
                                        constructorId = "red_bull",
                                        name = "Red Bull Racing",
                                    ),
                                    q1 = "1:30.031", q2 = "1:29.374", q3 = "1:29.179",
                                ),
                                // Knocked out in Q1 — Q2/Q3 are null on the wire.
                                JolpicaQualifyingResponseDto.QualifyingResultDto(
                                    number = "2",
                                    position = "20",
                                    driver = JolpicaQualifyingResponseDto.DriverDto(
                                        driverId = "sargeant",
                                        permanentNumber = "2",
                                        code = "SAR",
                                        givenName = "Logan",
                                        familyName = "Sargeant",
                                    ),
                                    constructor = JolpicaQualifyingResponseDto.ConstructorDto(
                                        constructorId = "williams",
                                        name = "Williams Racing",
                                    ),
                                    q1 = "1:30.770", q2 = null, q3 = null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val out = dto.toRoundQualifying()

        assertEquals(2024, out.year)
        assertEquals(1, out.round)
        assertEquals("Gulf Air Bahrain Grand Prix 2024", out.raceName)
        // Ergast qualifying has no separate quali date/time — the race's
        // date/time round through (unused by the UI).
        assertEquals("2024-03-02", out.qualyDate)
        assertEquals("15:00:00Z", out.qualyTime)
        assertEquals("bahrain", out.circuit.id)
        assertEquals("Bahrain International Circuit", out.circuit.name)
        assertEquals("Sakhir", out.circuit.city)
        assertEquals("Bahrain", out.circuit.country)
        // No length/corners on Jolpica standard — degraded fallback blanks them.
        assertEquals("", out.circuit.circuitLengthRaw)
        assertNull(out.circuit.corners)
        assertEquals(2, out.results.size)

        val pole = out.results[0]
        assertEquals(1, pole.gridPosition)
        assertEquals("1:30.031", pole.q1)
        assertEquals("1:29.374", pole.q2)
        assertEquals("1:29.179", pole.q3)
        assertEquals("max_verstappen", pole.driverId)
        assertEquals("Max Verstappen", pole.driverName)
        assertEquals("VER", pole.driverShortName)
        // `permanentNumber` is preferred over `number`.
        assertEquals(1, pole.driverNumber)
        assertEquals("red_bull", pole.teamId)
        assertEquals("Red Bull Racing", pole.teamName)

        val q1Knockout = out.results[1]
        assertEquals(20, q1Knockout.gridPosition)
        assertEquals("1:30.770", q1Knockout.q1)
        assertNull(q1Knockout.q2)
        assertNull(q1Knockout.q3)
        assertEquals("sargeant", q1Knockout.driverId)
        assertEquals("Logan Sargeant", q1Knockout.driverName)
        assertEquals("SAR", q1Knockout.driverShortName)
        assertEquals(2, q1Knockout.driverNumber)
        assertEquals("williams", q1Knockout.teamId)
        assertEquals("Williams Racing", q1Knockout.teamName)
    }

    @Test
    fun `toRoundQualifying falls back to car number when permanentNumber is absent`() {
        val dto = JolpicaQualifyingResponseDto(
            mrData = JolpicaQualifyingResponseDto.MrDataDto(
                raceTable = JolpicaQualifyingResponseDto.RaceTableDto(
                    races = listOf(
                        JolpicaQualifyingResponseDto.RaceDto(
                            qualifyingResults = listOf(
                                JolpicaQualifyingResponseDto.QualifyingResultDto(
                                    number = "8",
                                    position = "5",
                                    driver = JolpicaQualifyingResponseDto.DriverDto(
                                        driverId = "frentzen",
                                        // No permanentNumber — pre-permanent-number era.
                                        code = "FRE",
                                        givenName = "Heinz-Harald",
                                        familyName = "Frentzen",
                                    ),
                                    constructor = JolpicaQualifyingResponseDto.ConstructorDto(
                                        constructorId = "williams",
                                        name = "Williams",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val out = dto.toRoundQualifying()
        assertEquals(5, out.results[0].gridPosition)
        assertEquals(8, out.results[0].driverNumber)
    }

    @Test
    fun `toRoundQualifying yields empty results for a future race`() {
        val dto = JolpicaQualifyingResponseDto(
            mrData = JolpicaQualifyingResponseDto.MrDataDto(
                raceTable = JolpicaQualifyingResponseDto.RaceTableDto(
                    season = "2026",
                    round = "12",
                    races = emptyList(),
                ),
            ),
        )

        val out = dto.toRoundQualifying()
        assertEquals(2026, out.year)
        assertEquals(12, out.round)
        assertEquals("", out.raceName)
        assertTrue("expected empty results", out.results.isEmpty())
        assertEquals("", out.circuit.id)
    }

}