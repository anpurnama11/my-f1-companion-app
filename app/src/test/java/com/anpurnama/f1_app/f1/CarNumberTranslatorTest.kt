package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.CurrentDriversResponseDto
import com.anpurnama.f1_app.f1.data.CurrentDriverDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rung 1 mapper test for [CarNumberTranslator] — the alpha opaque id → Ergast
 * canonical id bridge used by the FP/SQ/SR result mappers.
 *
 * The translator is built from a season-matched f1api driver catalog
 * (`getDrivers(year)`), which returns Ergast ids (e.g. `max_verstappen`,
 * `red_bull`) for both current and historical seasons. The car number is the
 * unique per-season key.
 */
class CarNumberTranslatorTest {

    @Test
    fun `from builds a car-number keyed map from the catalog`() {
        val catalog = CurrentDriversResponseDto(
            season = 2024,
            drivers = listOf(
                CurrentDriverDto(driverId = "max_verstappen", number = 1, teamId = "red_bull"),
                CurrentDriverDto(driverId = "hamilton", number = 44, teamId = "ferrari"),
                CurrentDriverDto(driverId = "alonso", number = 14, teamId = "aston_martin"),
            ),
        )

        val translator = CarNumberTranslator.from(catalog)

        val max = translator.translate(1)
        assertEquals("max_verstappen", max?.driverId)
        assertEquals("red_bull", max?.teamId)

        val ham = translator.translate(44)
        assertEquals("hamilton", ham?.driverId)
        assertEquals("ferrari", ham?.teamId)
    }

    @Test
    fun `translate returns null for an unknown car number`() {
        val translator = CarNumberTranslator.from(
            CurrentDriversResponseDto(drivers = listOf(
                CurrentDriverDto(driverId = "max_verstappen", number = 1, teamId = "red_bull"),
            )),
        )
        assertNull(translator.translate(99))
    }

    @Test
    fun `translate returns null for a null car number`() {
        val translator = CarNumberTranslator.from(
            CurrentDriversResponseDto(drivers = listOf(
                CurrentDriverDto(driverId = "max_verstappen", number = 1, teamId = "red_bull"),
            )),
        )
        assertNull(translator.translate(null))
    }

    @Test
    fun `from skips drivers with no number or a blank driverId`() {
        val catalog = CurrentDriversResponseDto(
            drivers = listOf(
                CurrentDriverDto(driverId = "max_verstappen", number = 1, teamId = "red_bull"),
                // No number — the catalog occasionally omits it for reserves.
                CurrentDriverDto(driverId = "reserve", number = null, teamId = "red_bull"),
                // Blank driverId — defensive: should never happen, but must not crash.
                CurrentDriverDto(driverId = "", number = 7, teamId = "williams"),
            ),
        )

        val translator = CarNumberTranslator.from(catalog)
        assertEquals("max_verstappen", translator.translate(1)?.driverId)
        assertNull(translator.translate(7))
        assertNull(translator.translate(null))
    }

    @Test
    fun `EMPTY translator misses every lookup so callers keep alpha opaque ids`() {
        val translator = CarNumberTranslator.EMPTY
        assertNull(translator.translate(1))
        assertNull(translator.translate(44))
        assertNull(translator.translate(null))
    }

    @Test
    fun `from an empty catalog misses every lookup`() {
        val translator = CarNumberTranslator.from(CurrentDriversResponseDto())
        assertNull(translator.translate(1))
    }
}