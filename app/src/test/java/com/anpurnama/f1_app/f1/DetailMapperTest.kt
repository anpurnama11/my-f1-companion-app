package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.ConstructorsChampionshipResponseDto
import com.anpurnama.f1_app.f1.data.CurrentTeamsResponseDto
import com.anpurnama.f1_app.f1.data.CurrentTeamDto
import com.anpurnama.f1_app.f1.data.CurrentDriversResponseDto
import com.anpurnama.f1_app.f1.data.CurrentDriverDto
import com.anpurnama.f1_app.f1.data.DriversChampionshipResponseDto
import com.anpurnama.f1_app.core.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailMapperTest {

    @Test
    fun `driver detail joins current driver with championship snapshot by driver id`() {
        val drivers = CurrentDriversResponseDto(
            season = 2026,
            drivers = listOf(
                CurrentDriverDto(
                    driverId = "antonelli",
                    name = "Andrea",
                    surname = "Kimi Antonelli",
                    shortName = "ANT",
                    nationality = "Italy",
                    birthday = "2006-08-25",
                    number = 12,
                    teamId = "mercedes",
                ),
            ),
        )
        val championship = DriversChampionshipResponseDto(
            driversChampionship = listOf(
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
                    team = DriversChampionshipResponseDto.TeamInfoDto(
                        teamName = "Mercedes Formula 1 Team",
                        country = "Germany",
                    ),
                ),
            ),
        )

        val detail = (drivers.toDriverDetail("antonelli", championship) as Outcome.Success).data

        assertEquals("antonelli", detail.driverId)
        assertEquals("Andrea Kimi Antonelli", detail.name)
        assertEquals("Mercedes Formula 1 Team", detail.teamName)
        assertEquals(12, detail.number)
        assertEquals(204, detail.standing?.points)
        assertEquals(1, detail.standing?.position)
    }

    @Test
    fun `team detail preserves nullable history and joins constructor snapshot by team id`() {
        val teams = CurrentTeamsResponseDto(
            season = 2026,
            teams = listOf(
                CurrentTeamDto(
                    teamId = "cadillac",
                    teamName = "Cadillac Formula 1 Team",
                    teamNationality = "United States",
                    firstAppeareance = 2026,
                    constructorsChampionships = null,
                    driversChampionships = null,
                ),
            ),
        )
        val championship = ConstructorsChampionshipResponseDto(
            constructorsChampionship = listOf(
                ConstructorsChampionshipResponseDto.ConstructorsChampionshipEntryDto(
                    teamId = "cadillac",
                    points = 0,
                    position = 11,
                    wins = 0,
                    team = ConstructorsChampionshipResponseDto.TeamInfoDto(
                        teamName = "Cadillac Formula 1 Team",
                        country = "United States",
                    ),
                ),
            ),
        )

        val detail = (teams.toTeamDetail("cadillac", championship) as Outcome.Success).data

        assertEquals("cadillac", detail.teamId)
        assertEquals("Cadillac Formula 1 Team", detail.wordmark)
        assertEquals("United States", detail.country)
        assertEquals(2026, detail.firstAppearance)
        assertNull(detail.constructorsChampionships)
        assertEquals(0, detail.standing?.points)
        assertEquals(11, detail.standing?.position)
    }

    @Test
    fun `detail joins return a failure for an unknown stable id`() {
        val driverResult = CurrentDriversResponseDto().toDriverDetail(
            driverId = "missing",
            championship = DriversChampionshipResponseDto(),
        )
        val teamResult = CurrentTeamsResponseDto().toTeamDetail(
            teamId = "missing",
            championship = ConstructorsChampionshipResponseDto(),
        )

        assertTrue(driverResult is Outcome.Failure)
        assertTrue(teamResult is Outcome.Failure)
    }
}
