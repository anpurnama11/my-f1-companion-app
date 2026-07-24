package com.anpurnama.f1_app.f1.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionResultPresentationTest {
    private fun result(grid: String, position: String, status: String? = null) = RoundResult(
        position = position, points = 0, grid = grid, time = "+1.000",
        driverId = "driver", driverName = "Driver", driverShortName = "DRV", driverNumber = 1,
        teamId = "team", teamName = "Team", status = status,
    )

    @Test
    fun `pit lane starts show PL and no movement`() {
        val row = result("0", "5")
        assertEquals("PL", row.displayGrid())
        assertNull(row.positionChange())
    }

    @Test
    fun `status labels override dirty f1api time`() {
        assertEquals("DNF", result("3", "NC", "Retired").displayStatusOrTime())
        assertEquals("DNS", result("0", "NC", "Did not start").displayStatusOrTime())
        assertEquals("+1.000", result("3", "2", "Finished").displayStatusOrTime())
    }

    @Test
    fun `position change is computed even for classified status values`() {
        assertEquals(3, result("5", "2").positionChange())
        assertEquals(-2, result("2", "4").positionChange())
        assertEquals(0, result("2", "2").positionChange())
    }

    @Test
    fun `pit-stop presentation joins by driver id`() {
        val winner = result("2", "1").copy(
            driverId = "leclerc",
            driverShortName = "LEC",
        )
        val session = SessionResult(
            year = 2026,
            round = 1,
            raceName = "Bahrain GP",
            circuit = Circuit("bahrain", "Bahrain", "5412km", 15, "Sakhir", "Bahrain"),
            session = SessionType.Race,
            raceResults = listOf(winner),
        )

        assertEquals(winner, session.driverForPitstop(FastestPitstop("leclerc", 1.9)))
        assertNull(session.driverForPitstop(FastestPitstop("verstappen", 1.9)))
    }
}
