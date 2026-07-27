package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.JolpicaConstructorStandingsResponseDto
import com.anpurnama.f1_app.f1.toConstructorStandings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rung 1 mapper test for the constructors' championship DTO (Jolpica source).
 * Pure mapping.
 */
class ConstructorStandingsMapperTest {

    @Test
    fun `toConstructorStandings maps every entry into a domain model`() {
        val dto = JolpicaConstructorStandingsResponseDto(
            mrData = JolpicaConstructorStandingsResponseDto.MrDataDto(
                standingsTable = JolpicaConstructorStandingsResponseDto.StandingsTableDto(
                    standingsLists = listOf(
                        JolpicaConstructorStandingsResponseDto.StandingsListDto(
                            constructorStandings = listOf(
                                JolpicaConstructorStandingsResponseDto.ConstructorStandingEntryDto(
                                    position = "1",
                                    points = "358",
                                    wins = "8",
                                    constructor = JolpicaConstructorStandingsResponseDto.ConstructorDto(
                                        constructorId = "mercedes",
                                        name = "Mercedes Formula 1 Team",
                                        nationality = "German",
                                    ),
                                ),
                                JolpicaConstructorStandingsResponseDto.ConstructorStandingEntryDto(
                                    position = "2",
                                    points = "285",
                                    wins = "2",
                                    constructor = JolpicaConstructorStandingsResponseDto.ConstructorDto(
                                        constructorId = "ferrari",
                                        name = "Scuderia Ferrari",
                                        nationality = "Italian",
                                    ),
                                ),
                            ),
                        ),
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
        assertEquals("German", standings[0].country)
        assertEquals("ferrari", standings[1].teamId)
    }

    @Test
    fun `toConstructorStandings returns an empty list on an empty envelope`() {
        val dto = JolpicaConstructorStandingsResponseDto()
        assertEquals(emptyList<Any>(), dto.toConstructorStandings())
    }

    @Test
    fun `toConstructorStandings returns an empty list when StandingsLists is empty`() {
        val dto = JolpicaConstructorStandingsResponseDto(
            mrData = JolpicaConstructorStandingsResponseDto.MrDataDto(
                standingsTable = JolpicaConstructorStandingsResponseDto.StandingsTableDto(
                    standingsLists = emptyList(),
                ),
            ),
        )
        assertEquals(emptyList<Any>(), dto.toConstructorStandings())
    }
}
