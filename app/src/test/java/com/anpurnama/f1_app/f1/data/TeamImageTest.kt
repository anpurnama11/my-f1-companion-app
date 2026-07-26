package com.anpurnama.f1_app.f1.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Rung-1 unit tests for [teamImageUrl]. The slug map was probed live
 * on the CDN against the 2026 grid; these tests pin the
 * compile-time constants so a future rename in f1api.dev's
 * `teamId` namespace is caught immediately.
 */
class TeamImageTest {

    @Test
    fun `every 2026 teamId maps to a non-null URL on the right side`() {
        // 11 teams on the 2026 grid — see TEAM_IMAGE_SLUGS in TeamImage.kt
        val expected = mapOf(
            "audi"         to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/audi/2026audicarright.webp",
            "alpine"       to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/alpine/2026alpinecarright.webp",
            "aston_martin" to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/astonmartin/2026astonmartincarright.webp",
            "cadillac"     to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/cadillac/2026cadillaccarright.webp",
            "ferrari"      to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/ferrari/2026ferraricarright.webp",
            "haas"         to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/haas/2026haascarright.webp",
            "mclaren"      to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/mclaren/2026mclarencarright.webp",
            "mercedes"     to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/mercedes/2026mercedescarright.webp",
            "rb"           to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/racingbulls/2026racingbullscarright.webp",
            "red_bull"     to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/redbullracing/2026redbullracingcarright.webp",
            "williams"     to "https://media.formula1.com/image/upload/c_lfill,w_1320,q_auto/v1740000001/common/f1/2026/williams/2026williamscarright.webp",
        )
        for ((id, expectedUrl) in expected) {
            assertEquals(
                "expected URL for teamId \"$id\" to match",
                expectedUrl,
                teamImageUrl(id, 2026),
            )
        }
    }

    @Test
    fun `left side swaps the car suffix`() {
        val right = teamImageUrl("ferrari", 2026, side = "right")
        val left = teamImageUrl("ferrari", 2026, side = "left")
        assertNotNull(right)
        assertNotNull(left)
        assertEquals(
            right!!.replace("carright", "carleft"),
            left,
        )
    }

    @Test
    fun `year before 2026 returns null — v1 does not ship the legacy AEM path`() {
        assertNull(teamImageUrl("ferrari", 2025))
        assertNull(teamImageUrl("ferrari", 2024))
        assertNull(teamImageUrl("ferrari", 2020))
    }

    @Test
    fun `unknown teamId returns null so caller falls back to the swatch`() {
        assertNull(teamImageUrl("alpha_tauri", 2026))
        assertNull(teamImageUrl("kick_sauber", 2026))
        assertNull(teamImageUrl("new_constructor_2027", 2026))
    }

    @Test
    fun `invalid side throws so a typo is loud, not silent`() {
        assertThrows(IllegalArgumentException::class.java) {
            teamImageUrl("ferrari", 2026, side = "centre")
        }
        assertThrows(IllegalArgumentException::class.java) {
            teamImageUrl("ferrari", 2026, side = "")
        }
    }
}
