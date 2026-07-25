package com.anpurnama.f1_app

import com.anpurnama.f1_app.core.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit test for [parseRoundDeepLink] — the pure parser that
 * `MainActivity` uses to turn the Countdown widget's
 * `f1app://round/{year}/{round}` deep-link URI into a
 * [Route.RoundDetail]. JVM-only (no `android.net.Uri`).
 *
 * The cases are the ones that the advisor flagged as worth pinning
 * before shipping: the URI shape (host = "round", path = year/round)
 * is what `Uri.parse` actually returns on Android. A bad parser
 * could swap year/round, drop the host, or accept a wrong scheme —
 * all silent-failure modes.
 */
class ParseRoundDeepLinkTest {

    @Test
    fun `typical widget tap parses to RoundDetail with year and round`() {
        val route = parseRoundDeepLink("f1app://round/2026/1")
        assertEquals(Route.RoundDetail(year = 2026, round = 1), route)
    }

    @Test
    fun `large round numbers are preserved`() {
        assertEquals(
            Route.RoundDetail(year = 2026, round = 24),
            parseRoundDeepLink("f1app://round/2026/24"),
        )
    }

    @Test
    fun `two-digit years parse correctly`() {
        assertEquals(
            Route.RoundDetail(year = 2025, round = 7),
            parseRoundDeepLink("f1app://round/2025/7"),
        )
    }

    @Test
    fun `rejects http scheme`() {
        assertNull(parseRoundDeepLink("http://round/2026/1"))
        assertNull(parseRoundDeepLink("https://round/2026/1"))
    }

    @Test
    fun `rejects wrong host`() {
        assertNull(parseRoundDeepLink("f1app://other/2026/1"))
        assertNull(parseRoundDeepLink("f1app://rounds/2026/1"))
    }

    @Test
    fun `rejects missing path`() {
        assertNull(parseRoundDeepLink("f1app://round"))
        assertNull(parseRoundDeepLink("f1app://round/"))
    }

    @Test
    fun `rejects incomplete path (year only)`() {
        assertNull(parseRoundDeepLink("f1app://round/2026"))
    }

    @Test
    fun `rejects non-integer year`() {
        assertNull(parseRoundDeepLink("f1app://round/twenty-twenty-six/1"))
    }

    @Test
    fun `rejects non-integer round`() {
        assertNull(parseRoundDeepLink("f1app://round/2026/race-1"))
    }

    @Test
    fun `rejects empty string`() {
        assertNull(parseRoundDeepLink(""))
    }

    @Test
    fun `rejects garbage input`() {
        assertNull(parseRoundDeepLink("not-a-uri"))
        assertNull(parseRoundDeepLink("///"))
    }
}
