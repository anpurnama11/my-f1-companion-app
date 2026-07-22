package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.RoundResultsResponseDto
import com.anpurnama.f1_app.f1.toRoundResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Rung 1 mapper test: `/{year}/{round}/race` DTO → domain. Pure mapping,
 * no IO. The `position` field is kept as a String per the ticket contract
 * (handles "1" / "2" / "NC"); the `time` field is kept as a String
 * (handles "1:31:44" / "+22.457" / "+1 lap" / "DNF (1)"). The mapper is
 * `internal` so the test can reach it from the same module.
 *
 * The `RaceResult` model is what the Round detail screen renders as the
 * full P1–P20 grid AND what the Past list slices `[0..2]` from for the
 * podium. Verify the load-bearing fields end-to-end so a refactor in
 * one caller doesn't silently break the other.
 */
class RoundResultsMapperTest {

    @Test
    fun `toRoundResults maps the races envelope into the domain model`() {
        val dto = RoundResultsResponseDto(
            season = 2024,
            races = RoundResultsResponseDto.RacesDto(
                round = "1",
                date = "2024-03-02",
                time = "15:00:00Z",
                raceId = "bahrein2024",
                raceName = "Gulf Air Bahrain Grand Prix 2024",
                url = "https://en.wikipedia.org/wiki/2024BahrainGrandPrix",
                circuit = listOf(
                    RoundResultsResponseDto.RacesDto.CircuitDto(
                        circuitId = "bahrain",
                        circuitName = "Bahrain International Circuit",
                        country = "Bahrain",
                        city = "Sakhir",
                        circuitLength = "5412km",
                        corners = 15,
                    )
                ),
                results = listOf(
                    RoundResultsResponseDto.RacesDto.ResultDto(
                        position = "1", points = 26, grid = "1", time = "1:31:44",
                        fastLap = null, retired = null,
                        driver = RoundResultsResponseDto.RacesDto.ResultDto.DriverDto(
                            driverId = "maxverstappen", number = 33, shortName = "VER",
                            name = "Max", surname = "Verstappen",
                            nationality = "Netherlands", birthday = "1997-09-30",
                        ),
                        team = RoundResultsResponseDto.RacesDto.ResultDto.TeamDto(
                            teamId = "redbull", teamName = "Red Bull Racing",
                            nationality = "Austria", firstAppareance = 2005,
                        ),
                    ),
                    RoundResultsResponseDto.RacesDto.ResultDto(
                        position = "2", points = 18, grid = "5", time = "+22.457",
                        fastLap = null, retired = null,
                        driver = RoundResultsResponseDto.RacesDto.ResultDto.DriverDto(
                            driverId = "perez", number = 11, shortName = "PER",
                            name = "Sergio", surname = "Pérez",
                            nationality = "Mexico", birthday = "1990-01-26",
                        ),
                        team = RoundResultsResponseDto.RacesDto.ResultDto.TeamDto(
                            teamId = "redbull", teamName = "Red Bull Racing",
                            nationality = "Austria", firstAppareance = 2005,
                        ),
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
        assertEquals("Bahrain", out.circuit.country)
        assertEquals("5412km", out.circuit.circuitLengthRaw)
        assertEquals(15, out.circuit.corners)
        assertEquals(2, out.results.size)
        val winner = out.results[0]
        assertEquals("1", winner.position)
        assertEquals(26, winner.points)
        assertEquals("1", winner.grid)
        assertEquals("1:31:44", winner.time)
        assertEquals("maxverstappen", winner.driverId)
        assertEquals("Max Verstappen", winner.driverName)
        assertEquals("VER", winner.driverShortName)
        assertEquals(33, winner.driverNumber)
        assertEquals("redbull", winner.teamId)
        assertEquals("Red Bull Racing", winner.teamName)
    }

    @Test
    fun `toRoundResults keeps NC position as a string`() {
        // Retiree: position = "NC" (per ticket 03 spec, "position is a
        // String tolerating 'NC'"). The mapper must NOT filter / coerce.
        val dto = RoundResultsResponseDto(
            season = 2024,
            races = RoundResultsResponseDto.RacesDto(
                round = "1", date = "2024-03-02", time = "15:00:00Z",
                raceId = "bahrein2024", raceName = "Bahrain GP",
                url = null,
                circuit = listOf(
                    RoundResultsResponseDto.RacesDto.CircuitDto(
                        circuitId = "bahrain", circuitName = "Bahrain",
                        country = "Bahrain", city = "Sakhir",
                        circuitLength = "5412km", corners = 15,
                    )
                ),
                results = listOf(
                    RoundResultsResponseDto.RacesDto.ResultDto(
                        position = "NC", points = 0, grid = "5", time = "DNF (1)",
                        fastLap = null, retired = null,
                        driver = RoundResultsResponseDto.RacesDto.ResultDto.DriverDto(
                            driverId = "perez", number = 11, shortName = "PER",
                            name = "Sergio", surname = "Pérez",
                            nationality = "Mexico", birthday = "1990-01-26",
                        ),
                        team = RoundResultsResponseDto.RacesDto.ResultDto.TeamDto(
                            teamId = "redbull", teamName = "Red Bull Racing",
                            nationality = "Austria", firstAppareance = 2005,
                        ),
                    ),
                ),
            ),
        )

        val out = dto.toRoundResults()
        val retiree = out.results.single()
        assertEquals("NC", retiree.position)
        assertEquals("DNF (1)", retiree.time)
    }
}
