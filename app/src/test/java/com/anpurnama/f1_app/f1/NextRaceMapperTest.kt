package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.NextRaceResponseDto
import com.anpurnama.f1_app.f1.toNextRace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rung 1 mapper test: DTO → domain. The mapper is `internal` so the test
 * can reach it from the same module. Pure mapping, no IO.
 *
 * The `NextRace` model carries the current race context used by Homepage.
 */
class NextRaceMapperTest {

    @Test
    fun `toNextRace maps the inner race block into the domain model`() {
        val dto = NextRaceResponseDto(
            season = 2026,
            round = 11,
            race = listOf(
                NextRaceResponseDto.NextRaceInnerDto(
                    raceId = "hungarian2026",
                    raceName = "Formula 1 AWS Hungarian Grand Prix 2026",
                    round = 11,
                    laps = 70,
                    circuit = NextRaceResponseDto.NextRaceInnerDto.RaceCircuitDto(
                        circuitId = "hungaroring",
                        circuitName = "Hungaroring",
                        country = "Hungary",
                        city = "Mogyorod",
                        circuitLength = "4381km",
                        corners = 14,
                    ),
                    schedule = NextRaceResponseDto.NextRaceInnerDto.RaceScheduleDto(
                        race = NextRaceResponseDto.NextRaceInnerDto.RaceScheduleDto.RaceSessionDto(
                            date = "2026-07-26",
                            time = "13:00:00Z",
                        ),
                    ),
                ),
            ),
        )

        val next = dto.toNextRace()

        assertNotNull(next)
        assertEquals(2026, next!!.year)
        assertEquals(11, next.round)
        assertEquals("Formula 1 AWS Hungarian Grand Prix 2026", next.raceName)
        assertEquals("hungarian2026", next.raceId)
        assertEquals(70, next.laps)
        assertEquals("hungaroring", next.circuit.id)
        assertEquals("Hungaroring", next.circuit.name)
        assertEquals("Hungary", next.circuit.country)
        assertEquals("2026-07-26", next.raceDate)
        assertEquals("13:00:00Z", next.raceTime)
    }

    @Test
    fun `toNextRace returns null when the race list is empty`() {
        val dto = NextRaceResponseDto(season = 2026, round = 0, race = emptyList())
        assertNull(dto.toNextRace())
    }
}
