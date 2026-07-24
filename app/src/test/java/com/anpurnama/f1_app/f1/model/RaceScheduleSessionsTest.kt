package com.anpurnama.f1_app.f1.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceScheduleSessionsTest {
    private val slot = SessionSlot("2026-03-01", "12:00:00Z")

    @Test
    fun `non sprint weekend exposes practice 1 2 3 qualifying and race`() {
        val sessions = RaceSchedule(fp1 = slot, fp2 = slot, fp3 = slot, qualy = slot, race = slot)
            .activeSessions()
        assertEquals(
            listOf(SessionType.FP1, SessionType.FP2, SessionType.FP3, SessionType.Quali, SessionType.Race),
            sessions.map { it.type },
        )
    }

    @Test
    fun `sprint weekend replaces practice 2 and 3 with sprint qualifying and sprint`() {
        val sessions = RaceSchedule(fp1 = slot, fp2 = slot, fp3 = slot,
            sprintQualy = slot, sprintRace = slot, qualy = slot, race = slot).activeSessions()
        assertEquals(
            listOf(SessionType.FP1, SessionType.SprintQuali, SessionType.Sprint, SessionType.Quali, SessionType.Race),
            sessions.map { it.type },
        )
    }

    @Test
    fun `round mode uses race start and handles missing schedule`() {
        val upcoming = Race(1, "GP", Circuit("x", "Circuit", "5000km", 10, null, null), null, 50,
            RaceSchedule(race = slot))
        assertEquals(RoundMode.Upcoming, roundMode(upcoming, Instant.parse("2026-02-01T00:00:00Z")))
        assertEquals(RoundMode.Past, roundMode(upcoming, Instant.parse("2026-04-01T00:00:00Z")))
        assertTrue(roundMode(upcoming.copy(schedule = null), Instant.parse("2026-04-01T00:00:00Z")) == RoundMode.Upcoming)
    }
}
