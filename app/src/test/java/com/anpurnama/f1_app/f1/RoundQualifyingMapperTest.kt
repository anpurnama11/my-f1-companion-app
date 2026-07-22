package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.RoundQualifyingResponseDto
import com.anpurnama.f1_app.f1.toRoundQualifying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rung 1 mapper test: `/{year}/{round}/qualy` DTO → domain. Pure
 * mapping, no IO.
 *
 * Note the envelope-vs-race differences the ticket 03 spec calls out:
 *  - `circuit` is a single object here, NOT a one-element array
 *    (compare with /race's `circuit: [{...}]`).
 *  - `qualyResults` is ordered by `gridPosition` (Int, 1-based).
 *  - `q1`/`q2`/`q3` are dirty Strings or null when the driver didn't
 *    reach that segment (knocked out in Q1 has q2/q3 = null).
 */
class RoundQualifyingMapperTest {

    @Test
    fun `toRoundQualifying maps the qualy envelope into the domain model`() {
        val dto = RoundQualifyingResponseDto(
            season = 2024,
            races = RoundQualifyingResponseDto.RacesDto(
                round = "1",
                qualyDate = "2024-03-01",
                qualyTime = "16:00:00Z",
                raceId = "bahrein2024",
                raceName = "Gulf Air Bahrain Grand Prix 2024",
                url = "https://en.wikipedia.org/wiki/2024BahrainGrandPrix",
                circuit = RoundQualifyingResponseDto.RacesDto.CircuitDto(
                    circuitId = "bahrain",
                    circuitName = "Bahrain International Circuit",
                    country = "Bahrain",
                    city = "Sakhir",
                    circuitLength = "5412km",
                    corners = 15,
                ),
                qualyResults = listOf(
                    RoundQualifyingResponseDto.RacesDto.QualyResultDto(
                        classificationId = 1,
                        driverId = "maxverstappen",
                        teamId = "redbull",
                        q1 = "1:30.031", q2 = "1:29.374", q3 = "1:29.179",
                        gridPosition = 1,
                        driver = RoundQualifyingResponseDto.RacesDto.QualyResultDto.DriverDto(
                            driverId = "maxverstappen", number = 33, shortName = "VER",
                            name = "Max", surname = "Verstappen",
                        ),
                        team = RoundQualifyingResponseDto.RacesDto.QualyResultDto.TeamDto(
                            teamId = "redbull", teamName = "Red Bull Racing",
                        ),
                    ),
                    // Knocked out in Q1 — q2/q3 are null on the wire.
                    RoundQualifyingResponseDto.RacesDto.QualyResultDto(
                        classificationId = 17,
                        driverId = "sargeant",
                        teamId = "williams",
                        q1 = "1:30.770", q2 = null, q3 = null,
                        gridPosition = 18,
                        driver = RoundQualifyingResponseDto.RacesDto.QualyResultDto.DriverDto(
                            driverId = "sargeant", number = 2, shortName = "SAR",
                            name = "Logan", surname = "Sargeant",
                        ),
                        team = RoundQualifyingResponseDto.RacesDto.QualyResultDto.TeamDto(
                            teamId = "williams", teamName = "Williams Racing",
                        ),
                    ),
                ),
            ),
        )

        val out = dto.toRoundQualifying()

        assertEquals(2024, out.year)
        assertEquals(1, out.round)
        assertEquals("Gulf Air Bahrain Grand Prix 2024", out.raceName)
        assertEquals("2024-03-01", out.qualyDate)
        assertEquals("16:00:00Z", out.qualyTime)
        assertEquals("bahrain", out.circuit.id)
        assertEquals(2, out.results.size)

        val pole = out.results[0]
        assertEquals(1, pole.gridPosition)
        assertEquals("1:30.031", pole.q1)
        assertEquals("1:29.374", pole.q2)
        assertEquals("1:29.179", pole.q3)
        assertEquals("maxverstappen", pole.driverId)
        assertEquals("Max Verstappen", pole.driverName)
        assertEquals("VER", pole.driverShortName)
        assertEquals(33, pole.driverNumber)
        assertEquals("redbull", pole.teamId)
        assertEquals("Red Bull Racing", pole.teamName)

        val q1Knockout = out.results[1]
        assertEquals(18, q1Knockout.gridPosition)
        assertEquals("1:30.770", q1Knockout.q1)
        assertNull(q1Knockout.q2)
        assertNull(q1Knockout.q3)
    }
}
