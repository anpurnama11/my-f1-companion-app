package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.DriversChampionshipResponseDto
import com.anpurnama.f1_app.f1.toDriverStandings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rung 1 mapper test for the drivers' championship DTO. Pure mapping.
 */
class DriverStandingsMapperTest {

    @Test
    fun `toDriverStandings maps every entry into a domain model`() {
        val dto = DriversChampionshipResponseDto(
            season = 2026,
            driverschampionship = listOf(
                DriversChampionshipResponseDto.DriversChampionshipEntryDto(
                    driverId = "antonelli",
                    teamId = "mercedes",
                    points = 204,
                    position = 1,
                    wins = 6,
                    driver = DriversChampionshipResponseDto.DriverInfoDto(
                        name = "Andrea",
                        surname = "Kimi Antonelli",
                        shortName = "ANT",
                        number = 12,
                    ),
                ),
                DriversChampionshipResponseDto.DriversChampionshipEntryDto(
                    driverId = "hamilton",
                    teamId = "ferrari",
                    points = 159,
                    position = 2,
                    wins = 1,
                    driver = DriversChampionshipResponseDto.DriverInfoDto(
                        name = "Lewis",
                        surname = "Hamilton",
                        shortName = "HAM",
                        number = 44,
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
    fun `toDriverStandings tolerates a missing surname`() {
        // The schema has `name` and `surname` both optional; a partial
        // row should not crash the mapper. Trim the trailing space.
        val dto = DriversChampionshipResponseDto(
            driverschampionship = listOf(
                DriversChampionshipResponseDto.DriversChampionshipEntryDto(
                    driverId = "x",
                    teamId = "y",
                    driver = DriversChampionshipResponseDto.DriverInfoDto(name = "Solo"),
                ),
            ),
        )
        val standings = dto.toDriverStandings()
        assertEquals("Solo", standings[0].driverName)
    }

    @Test
    fun `toDriverStandings returns an empty list on an empty envelope`() {
        val dto = DriversChampionshipResponseDto(season = 2026)
        assertEquals(emptyList<Any>(), dto.toDriverStandings())
    }
}
