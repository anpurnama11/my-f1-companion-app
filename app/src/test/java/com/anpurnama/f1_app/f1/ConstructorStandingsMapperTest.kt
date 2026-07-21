package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.ConstructorsChampionshipResponseDto
import com.anpurnama.f1_app.f1.toConstructorStandings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rung 1 mapper test for the constructors' championship DTO. Pure mapping.
 */
class ConstructorStandingsMapperTest {

    @Test
    fun `toConstructorStandings maps every entry into a domain model`() {
        val dto = ConstructorsChampionshipResponseDto(
            season = 2026,
            constructorschampionship = listOf(
                ConstructorsChampionshipResponseDto.ConstructorsChampionshipEntryDto(
                    teamId = "mercedes",
                    points = 358,
                    position = 1,
                    wins = 8,
                    team = ConstructorsChampionshipResponseDto.TeamInfoDto(
                        teamName = "Mercedes Formula 1 Team",
                        country = "Germany",
                    ),
                ),
                ConstructorsChampionshipResponseDto.ConstructorsChampionshipEntryDto(
                    teamId = "ferrari",
                    points = 285,
                    position = 2,
                    wins = 2,
                    team = ConstructorsChampionshipResponseDto.TeamInfoDto(
                        teamName = "Scuderia Ferrari",
                        country = "Italy",
                    ),
                ),
            ),
        )

        val standings = dto.toConstructorStandings()
        assertEquals(2, standings.size)
        assertEquals("mercedes", standings[0].teamId)
        assertEquals(1, standings[0].position)
        assertEquals(358, standings[0].points)
        assertEquals(8, standings[0].wins)
        assertEquals("Mercedes Formula 1 Team", standings[0].teamName)
        assertEquals("Germany", standings[0].country)
        assertEquals("ferrari", standings[1].teamId)
    }

    @Test
    fun `toConstructorStandings returns an empty list on an empty envelope`() {
        val dto = ConstructorsChampionshipResponseDto(season = 2026)
        assertEquals(emptyList<Any>(), dto.toConstructorStandings())
    }
}
