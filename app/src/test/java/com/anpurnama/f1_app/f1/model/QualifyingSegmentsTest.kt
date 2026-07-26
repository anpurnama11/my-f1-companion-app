package com.anpurnama.f1_app.f1.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualifyingSegmentsTest {
    @Test
    fun `q1 contains every driver with q1 time and marks q1 eliminations`() {
        val tabs = listOf(
            quali("fast", grid = 1, q1 = "1:30.000", q2 = "1:29.000", q3 = "1:28.000"),
            quali("eliminated", grid = 16, q1 = "1:33.000"),
            quali("middle", grid = 7, q1 = "1:31.000", q2 = "1:30.000"),
        ).toQualifyingSegmentTabs()

        val q1 = tabs.single { it.segment == QualifyingSegment.Q1 }
        assertEquals(listOf("fast", "middle", "eliminated"), q1.rows.map { it.driverId })
        assertEquals(2, q1.advancedCount)
        assertEquals(1, q1.eliminatedCount)
        assertFalse(q1.rows[0].eliminated)
        assertTrue(q1.rows[2].eliminated)
    }

    @Test
    fun `q2 and q3 include only drivers with segment times`() {
        val tabs = listOf(
            quali("pole", grid = 1, q1 = "1:31.000", q2 = "1:30.000", q3 = "1:29.000"),
            quali("q2out", grid = 11, q1 = "1:32.000", q2 = "1:31.000"),
            quali("q1out", grid = 16, q1 = "1:33.000"),
        ).toQualifyingSegmentTabs()

        val q2 = tabs.single { it.segment == QualifyingSegment.Q2 }
        val q3 = tabs.single { it.segment == QualifyingSegment.Q3 }
        assertEquals(listOf("pole", "q2out"), q2.rows.map { it.driverId })
        assertEquals(listOf("pole"), q3.rows.map { it.driverId })
        assertFalse(q2.rows.first().eliminated)
        assertTrue(q2.rows.last().eliminated)
        assertFalse(q3.rows.single().eliminated)
    }

    @Test
    fun `segment positions are recomputed from segment time not final grid`() {
        val tabs = listOf(
            quali("pole", grid = 1, q1 = "1:31.000", q2 = "1:30.000", q3 = "1:29.500"),
            quali("q3-fastest", grid = 2, q1 = "1:32.000", q2 = "1:31.000", q3 = "1:29.000"),
        ).toQualifyingSegmentTabs()

        val q3 = tabs.single { it.segment == QualifyingSegment.Q3 }
        assertEquals("q3-fastest", q3.rows.first().driverId)
        assertEquals(1, q3.rows.first().segmentPosition)
        assertEquals(2, q3.rows.first().overallPosition)
    }

    @Test
    fun `lap parser accepts common and dirty qualifying time forms`() {
        assertEquals(90_031L, parseQualifyingLapTimeMillis("1:30.031"))
        assertEquals(90_031L, parseQualifyingLapTimeMillis("1:30:031"))
        assertEquals(59_999L, parseQualifyingLapTimeMillis("59.999"))
        assertEquals(null, parseQualifyingLapTimeMillis("DNF"))
    }

    @Test
    fun `unparsed times sort after parsed times but keep original relative order`() {
        val tabs = listOf(
            quali("bad-one", grid = 1, q1 = "DNF"),
            quali("valid", grid = 2, q1 = "1:30.000"),
            quali("bad-two", grid = 3, q1 = "NO TIME"),
        ).toQualifyingSegmentTabs()

        assertEquals(
            listOf("valid", "bad-one", "bad-two"),
            tabs.single { it.segment == QualifyingSegment.Q1 }.rows.map { it.driverId },
        )
    }

    @Test
    fun `blank segment time still means the driver reached that segment`() {
        val tabs = listOf(
            quali("pole", grid = 1, q1 = "1:31.000", q2 = "1:30.000", q3 = "1:29.000"),
            quali("q3-no-time", grid = 10, q1 = "1:32.000", q2 = "1:31.000", q3 = ""),
            quali("q2out", grid = 11, q1 = "1:33.000", q2 = "1:32.000", q3 = null),
        ).toQualifyingSegmentTabs()

        val q3 = tabs.single { it.segment == QualifyingSegment.Q3 }
        assertEquals(listOf("pole", "q3-no-time"), q3.rows.map { it.driverId })
        assertEquals("", q3.rows.last().time)
        assertFalse(q3.rows.last().eliminated)
    }

    private fun quali(
        driverId: String,
        grid: Int,
        q1: String?,
        q2: String? = null,
        q3: String? = null,
    ) = QualifyingResult(
        gridPosition = grid,
        q1 = q1,
        q2 = q2,
        q3 = q3,
        driverId = driverId,
        driverName = driverId.replaceFirstChar { it.uppercase() },
        driverShortName = driverId.take(3).uppercase(),
        driverNumber = grid,
        teamId = "team-$driverId",
        teamName = "Team $driverId",
    )
}
