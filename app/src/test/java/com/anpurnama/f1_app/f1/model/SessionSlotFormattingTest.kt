package com.anpurnama.f1_app.f1.model

import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSlotFormattingTest {
    @Test
    fun `session slot converts UTC date and time to requested local zone`() {
        val slot = SessionSlot("2024-03-01", "23:00:00Z")

        assertEquals(
            "Sat 2 Mar · 08:00",
            slot.toDeviceLocalLabel(TimeZone.of("Asia/Tokyo")),
        )
    }

    @Test
    fun `malformed slot has an explicit unavailable label`() {
        assertEquals(
            "Time unavailable",
            SessionSlot("not-a-date", "not-a-time").toDeviceLocalLabel(TimeZone.UTC),
        )
    }
}
