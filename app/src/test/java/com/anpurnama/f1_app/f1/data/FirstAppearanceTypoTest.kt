package com.anpurnama.f1_app.f1.data

import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Adversarial verification: the DTOs use the misspelled key
 * `firstAppareance` (no `r` after `App`) as the `@SerialName`, in
 * three places:
 *
 *   - `CircuitDto.firstGrandPrix`           (Dtos.kt:52)
 *   - `RoundResultsResponseDto.TeamDto`     (Dtos.kt:247)
 *   - `RoundQualifyingResponseDto.TeamDto`  (Dtos.kt:311)
 *
 * The actual wire field is `firstAppearance` (with `r`). The HTTP
 * client is configured with `ignoreUnknownKeys = true`
 * (HttpClientFactory.kt:54), so the correct field is silently
 * dropped and the typo field is parsed.
 *
 * This test pins the silent-drop behavior: the correct wire field
 * (`firstAppearance`) is ignored; only the typo field
 * (`firstAppareance`) populates the Kotlin val. The fix is to
 * rename the `@SerialName` to `"firstAppearance"` (and the Kotlin
 * val from `firstAppareance` to `firstAppearance`).
 *
 * Risk: the field is read by no current code path (the team model
 * doesn't surface `firstAppareance` anywhere in the UI today), so
 * the typo is dormant. Ticket 05 (my-team tab + favorites) and the
 * team-detail page may start reading it — at which point the value
 * will be null for every team. The fix should land before ticket 05.
 *
 * ponytail: this test is a regression guard for the fix, not a
 * behavior pin. When the typo is corrected, the test must be
 * updated to assert the corrected field name.
 */
class FirstAppearanceTypoTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `correct wire field firstAppearance is silently dropped by CircuitDto`() {
        val body = """
            { "circuitId": "bahrain", "circuitName": "Bahrain",
              "circuitLength": "5412km", "corners": 15,
              "firstAppearance": 2004 }
        """.trimIndent()
        val dto = json.decodeFromString<CircuitDto>(body)
        // The correct field is silently dropped — the DTO's
        // @SerialName is the typo, so `firstGrandPrix` stays null.
        assertNull(
            "expected firstGrandPrix to be null (correct field dropped), was ${dto.firstGrandPrix}",
            dto.firstGrandPrix,
        )
    }

    @Test
    fun `typo wire field firstAppareance populates CircuitDto firstGrandPrix`() {
        val body = """
            { "circuitId": "bahrain", "circuitName": "Bahrain",
              "circuitLength": "5412km", "corners": 15,
              "firstAppareance": 2004 }
        """.trimIndent()
        val dto = json.decodeFromString<CircuitDto>(body)
        // The typo field is parsed — but only because the @SerialName
        // matches the typo, not the correct spelling. If the wire
        // ever sends the correct spelling, the field will be null.
        assertNotNull(
            "expected firstGrandPrix to be non-null (typo field parsed), was ${dto.firstGrandPrix}",
            dto.firstGrandPrix,
        )
        assertEquals(2004, dto.firstGrandPrix)
    }
}
