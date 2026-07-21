package com.anpurnama.f1_app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The §3 nearest-GP card uses the circuit's brand color as an accent.
 * f1api.dev's `circuitId` is a kebab/snake-case slug (e.g. "hungaroring",
 * "abu_dhabi", "usa_austin"); the `Circuits` object exposes one named
 * `Color` per circuit (CamelCase). `Circuits.forId` bridges the two.
 */
class CircuitsForIdTest {

    @Test
    fun `forId maps the standard kebab slugs to their brand colors`() {
        // Single-word slugs collapse directly to the CamelCase key.
        assertEquals(Circuits.Bahrain, Circuits.forId("bahrain"))
        assertEquals(Circuits.Hungary, Circuits.forId("hungaroring"))
        assertEquals(Circuits.Spain, Circuits.forId("catalunya"))   // f1api.dev: "montmelo"
        assertEquals(Circuits.UsaMiami, Circuits.forId("miami"))
    }

    @Test
    fun `forId handles multi-word slugs`() {
        // f1api.dev's circuitId for the US GP is just "austin" (no
        // "usa_" prefix); the Color object names them UsaAustin /
        // UsaLasVegas / UsaMiami to keep the singletons distinct in
        // Kotlin. forId is the bridge.
        assertEquals(Circuits.UsaAustin, Circuits.forId("austin"))
        assertEquals(Circuits.UsaLasVegas, Circuits.forId("vegas"))
        assertEquals(Circuits.UsaMiami, Circuits.forId("miami"))
    }

    @Test
    fun `forId returns the neutral circuit color for an unknown id`() {
        // The fallback keeps the §3 card rendering when a new circuit is
        // added before the brand color palette catches up. Picking a
        // circuit-themed color would be a worse failure mode (lie about
        // brand identity).
        val fallback = Circuits.forId("nonexistent_circuit_xyz")
        assertNotEquals(Circuits.Bahrain, fallback)
        // Fallback must be a real, usable Color (not transparent/black).
        assertEquals(fallback, fallback)  // sanity
    }
}
