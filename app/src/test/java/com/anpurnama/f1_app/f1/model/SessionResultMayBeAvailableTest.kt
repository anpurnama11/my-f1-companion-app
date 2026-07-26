package com.anpurnama.f1_app.f1.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionResultMayBeAvailableTest {

    private val slot = SessionSlot("2026-07-05", "12:00:00Z")
    private val startSeconds = Instant.parse("2026-07-05T12:00:00Z").epochSeconds

    private fun at(offsetSeconds: Long): Instant =
        Instant.fromEpochSeconds(startSeconds + offsetSeconds)

    @Test
    fun `future slot never offers results`() {
        // one hour before start
        val now = at(-3600)
        SessionType.entries.forEach { type ->
            assertFalse("$type should not offer results before start", sessionResultMayBeAvailable(type, slot, now))
        }
    }

    @Test
    fun `FP and Sprint sessions offer results only past 6h buffer`() {
        val types = listOf(
            SessionType.FP1, SessionType.FP2, SessionType.FP3,
            SessionType.SprintQuali, SessionType.Sprint,
        )
        // 5h59m after start — still inside the 6h buffer
        val withinBuffer = at(6 * 3600 - 60)
        types.forEach { type ->
            assertFalse("$type within 6h buffer", sessionResultMayBeAvailable(type, slot, withinBuffer))
        }
        // exactly 6h after start — boundary inclusive
        val atBuffer = at(6 * 3600)
        types.forEach { type ->
            assertTrue("$type at 6h boundary", sessionResultMayBeAvailable(type, slot, atBuffer))
        }
        // well past buffer
        val pastBuffer = at(24 * 3600)
        types.forEach { type ->
            assertTrue("$type past 6h buffer", sessionResultMayBeAvailable(type, slot, pastBuffer))
        }
    }

    @Test
    fun `Quali offers results only past 12h buffer`() {
        // 11h after start — inside the 12h buffer
        val withinBuffer = at(11 * 3600)
        assertFalse("quali within 12h buffer", sessionResultMayBeAvailable(SessionType.Quali, slot, withinBuffer))
        // exactly 12h after start — boundary inclusive
        val atBuffer = at(12 * 3600)
        assertTrue("quali at 12h boundary", sessionResultMayBeAvailable(SessionType.Quali, slot, atBuffer))
        // well past buffer
        val pastBuffer = at(36 * 3600)
        assertTrue("quali past 12h buffer", sessionResultMayBeAvailable(SessionType.Quali, slot, pastBuffer))
    }

    @Test
    fun `Race row is never gated by the helper`() {
        // Race is owned by roundMode, so even far past start the helper
        // must not surface a button for it.
        val farPast = at(7 * 24 * 3600)
        assertFalse(sessionResultMayBeAvailable(SessionType.Race, slot, farPast))
    }

    @Test
    fun `null slot never offers results`() {
        SessionType.entries.forEach { type ->
            assertFalse("$type with null slot", sessionResultMayBeAvailable(type, SessionSlot(null, null), at(0)))
        }
    }

    @Test
    fun `malformed slot never offers results`() {
        val malformed = SessionSlot("not-a-date", "bad")
        SessionType.entries.forEach { type ->
            assertFalse("$type with malformed slot", sessionResultMayBeAvailable(type, malformed, at(0)))
        }
    }
}