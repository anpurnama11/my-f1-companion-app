package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.JolpicaDriverStandingsResponseDto
import com.anpurnama.f1_app.f1.toDriverStandings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rung 1 mapper test for the drivers' championship DTO (Jolpica source).
 * Pure mapping.
 */
class DriverStandingsMapperTest {

    @Test
    fun `toDriverStandings maps every entry into a domain model`() {
        val dto = JolpicaDriverStandingsResponseDto(
            mrData = JolpicaDriverStandingsResponseDto.MrDataDto(
                standingsTable = JolpicaDriverStandingsResponseDto.StandingsTableDto(
                    standingsLists = listOf(
                        JolpicaDriverStandingsResponseDto.StandingsListDto(
                            driverStandings = listOf(
                                JolpicaDriverStandingsResponseDto.DriverStandingEntryDto(
                                    position = "1",
                                    points = "204",
                                    wins = "6",
                                    driver = JolpicaDriverStandingsResponseDto.DriverDto(
                                        driverId = "antonelli",
                                        permanentNumber = "12",
                                        code = "ANT",
                                        givenName = "Andrea Kimi",
                                        familyName = "Antonelli",
                                    ),
                                    constructors = listOf(
                                        JolpicaDriverStandingsResponseDto.ConstructorDto(
                                            constructorId = "mercedes",
                                            name = "Mercedes",
                                        ),
                                    ),
                                ),
                                JolpicaDriverStandingsResponseDto.DriverStandingEntryDto(
                                    position = "2",
                                    points = "159",
                                    wins = "1",
                                    driver = JolpicaDriverStandingsResponseDto.DriverDto(
                                        driverId = "hamilton",
                                        permanentNumber = "44",
                                        code = "HAM",
                                        givenName = "Lewis",
                                        familyName = "Hamilton",
                                    ),
                                    constructors = listOf(
                                        JolpicaDriverStandingsResponseDto.ConstructorDto(
                                            constructorId = "ferrari",
                                            name = "Ferrari",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val standings = dto.toDriverStandings()
        assertEquals(2, standings.size)

        val first = standings[0]
        assertEquals("antonelli", first.driverId)
        assertEquals("mercedes", first.teamId)
        assertEquals(1, first.position)
        assertEquals(204, first.points)
        assertEquals(6, first.wins)
        assertEquals("Andrea Kimi Antonelli", first.driverName)
        assertEquals("ANT", first.driverShortName)
        assertEquals(12, first.driverNumber)

        val second = standings[1]
        assertEquals("hamilton", second.driverId)
        assertEquals("ferrari", second.teamId)
        assertEquals("Lewis Hamilton", second.driverName)
    }

    @Test
    fun `toDriverStandings tolerates a missing givenName`() {
        val dto = JolpicaDriverStandingsResponseDto(
            mrData = JolpicaDriverStandingsResponseDto.MrDataDto(
                standingsTable = JolpicaDriverStandingsResponseDto.StandingsTableDto(
                    standingsLists = listOf(
                        JolpicaDriverStandingsResponseDto.StandingsListDto(
                            driverStandings = listOf(
                                JolpicaDriverStandingsResponseDto.DriverStandingEntryDto(
                                    driver = JolpicaDriverStandingsResponseDto.DriverDto(
                                        driverId = "x",
                                        code = "XYZ",
                                    ),
                                    constructors = listOf(
                                        JolpicaDriverStandingsResponseDto.ConstructorDto(
                                            constructorId = "y",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val standings = dto.toDriverStandings()
        assertEquals("XYZ", standings[0].driverName)
    }

    @Test
    fun `toDriverStandings returns an empty list on an empty envelope`() {
        val dto = JolpicaDriverStandingsResponseDto()
        assertEquals(emptyList<Any>(), dto.toDriverStandings())
    }

    @Test
    fun `toDriverStandings returns an empty list when StandingsLists is empty`() {
        val dto = JolpicaDriverStandingsResponseDto(
            mrData = JolpicaDriverStandingsResponseDto.MrDataDto(
                standingsTable = JolpicaDriverStandingsResponseDto.StandingsTableDto(
                    standingsLists = emptyList(),
                ),
            ),
        )
        assertEquals(emptyList<Any>(), dto.toDriverStandings())
    }
}
