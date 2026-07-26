package com.anpurnama.f1_app.f1.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Rung-1 unit tests for [driverRef] + [driverImageUrl]. The slug
 * rule was probed live on the CDN for all 22 drivers on the 2026
 * grid; these tests pin the slug math and the URL builder so a
 * future API rename or a regex mistake is caught immediately.
 *
 * See [lode/wayfinder/f1app/cloudinary-headshot-paths.md](../../../../../../../../wayfinder/f1app/cloudinary-headshot-paths.md)
 * (Pass 3, Section B) for the full 22-driver table these tests
 * are distilled from.
 */
class DriverImageTest {

    // ─── driverRef ─────────────────────────────────────────────────────

    @Test
    fun `driverRef is a lowercase take-three plus 01`() {
        assertEquals("maxver01", driverRef("Max", "Verstappen"))
        assertEquals("lannor01", driverRef("Lando", "Norris"))
        assertEquals("lewham01", driverRef("Lewis", "Hamilton"))
        assertEquals("feralo01", driverRef("Fernando", "Alonso"))
    }

    @Test
    fun `driverRef takes only 3 characters even when name is long`() {
        // Antonelli: name "Andrea" → "and", surname "Antonelli" → "ant"
        assertEquals("andant01", driverRef("Andrea", "Antonelli"))
    }

    @Test
    fun `driverRef takes the last word of a multi-word surname — Antonelli`() {
        // f1api.dev's antonelli entry: name "Andrea", surname "Kimi
        // Antonelli" (multi-word). The CDN slug is "andant01" (8
        // chars: "and" + "ant" from "Antonelli" + "01"), not
        // "andkim01" which is what take(3) of the full surname would
        // give. This is the only multi-word surname on the 2026 grid;
        // verified live against the CDN.
        assertEquals("andant01", driverRef("Andrea", "Kimi Antonelli"))
    }

    @Test
    fun `driverRef strips combining marks via NFKD so accented surnames are pure ASCII`() {
        // Pérez is the only accented surname on the 2026 grid; the
        // canonical slug is `serper01` (NFKD + strip combining marks).
        assertEquals("serper01", driverRef("Sergio", "Pérez"))
    }

    @Test
    fun `driverRef returns null for blank name or surname so the caller falls back`() {
        assertNull(driverRef("", "Verstappen"))
        assertNull(driverRef("Max", ""))
        assertNull(driverRef("   ", "Verstappen"))
        assertNull(driverRef("Max", "   "))
    }

    // ─── driverImageUrl ────────────────────────────────────────────────

    @Test
    fun `driverImageUrl builds the canonical Cloudinary URL for Perez in 2026`() {
        // This is the explicit example from the ticket's "Verify before
        // shipping" section. Cheap insurance: if anyone touches the
        // path shape or the slug math, this test breaks immediately.
        val expected = "https://media.formula1.com/image/upload/" +
            "c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/" +
            "cadillac/serper01/2026cadillacserper01right.webp"
        assertEquals(
            expected,
            driverImageUrl("Sergio", "Pérez", "cadillac", 2026),
        )
    }

    @Test
    fun `driverImageUrl matches the slug table for representative drivers`() {
        // Sample of the 22-driver probe table — most common f1api.dev
        // teamIds. The full set is in the research doc.
        assertEquals(
            "https://media.formula1.com/image/upload/" +
                "c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/" +
                "redbullracing/maxver01/2026redbullracingmaxver01right.webp",
            driverImageUrl("Max", "Verstappen", "red_bull", 2026),
        )
        assertEquals(
            "https://media.formula1.com/image/upload/" +
                "c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/" +
                "mclaren/lannor01/2026mclarenlannor01right.webp",
            driverImageUrl("Lando", "Norris", "mclaren", 2026),
        )
        assertEquals(
            "https://media.formula1.com/image/upload/" +
                "c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/" +
                "audi/nichul01/2026audinichul01right.webp",
            driverImageUrl("Nico", "Hulkenberg", "audi", 2026),
        )
    }

    @Test
    fun `driverImageUrl uses left side when requested`() {
        val right = driverImageUrl("Max", "Verstappen", "red_bull", 2026, side = "right")
        val left = driverImageUrl("Max", "Verstappen", "red_bull", 2026, side = "left")
        assertEquals(right!!.replace("right.webp", "left.webp"), left)
    }

    @Test
    fun `driverImageUrl returns null for year before 2026`() {
        assertNull(driverImageUrl("Max", "Verstappen", "red_bull", 2025))
        assertNull(driverImageUrl("Max", "Verstappen", "red_bull", 2020))
    }

    @Test
    fun `driverImageUrl returns null for unknown teamId`() {
        assertNull(driverImageUrl("Max", "Verstappen", "alpha_tauri", 2026))
    }

    @Test
    fun `driverImageUrl returns null for blank name or surname`() {
        assertNull(driverImageUrl("", "Verstappen", "red_bull", 2026))
        assertNull(driverImageUrl("Max", "", "red_bull", 2026))
    }

    @Test
    fun `invalid side throws so a typo is loud, not silent`() {
        assertThrows(IllegalArgumentException::class.java) {
            driverImageUrl("Max", "Verstappen", "red_bull", 2026, side = "centre")
        }
    }
}
