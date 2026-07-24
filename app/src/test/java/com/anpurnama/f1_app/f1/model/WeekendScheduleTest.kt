package com.anpurnama.f1_app.f1.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rung 1: pure mapping / domain logic. The Homepage §1 countdown card
 * hinges on `nextUpcoming` picking the right session — one test per
 * branch, no fixtures beyond the session list.
 */
class WeekendScheduleTest {

    @Test
    fun `nextUpcoming returns the first session whose start is in the future`() {
        val now = Instant.parse("2026-07-24T10:00:00Z")
        val schedule = WeekendSchedule(
            listOf(
                SessionTime(SessionType.FP1, Instant.parse("2026-07-24T11:30:00Z")),
                SessionTime(SessionType.FP2, Instant.parse("2026-07-24T15:00:00Z")),
                SessionTime(SessionType.Race, Instant.parse("2026-07-26T13:00:00Z")),
            )
        )
        assertEquals("Practice 1", schedule.nextUpcoming(now)?.label)
    }

    @Test
    fun `nextUpcoming returns null when every session has started`() {
        val now = Instant.parse("2026-07-26T14:00:00Z")
        val schedule = WeekendSchedule(
            listOf(
                SessionTime(SessionType.FP1, Instant.parse("2026-07-24T11:30:00Z")),
                SessionTime(SessionType.Race, Instant.parse("2026-07-26T13:00:00Z")),
            )
        )
        assertNull(schedule.nextUpcoming(now))
    }
}
