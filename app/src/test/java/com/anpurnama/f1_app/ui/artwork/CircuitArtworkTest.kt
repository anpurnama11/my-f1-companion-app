package com.anpurnama.f1_app.ui.artwork

import com.anpurnama.f1_app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitArtworkTest {
    @Test
    fun `every bundled circuit id resolves to non placeholder artwork`() {
        val ids = listOf(
            "abudhabi", "albert_park", "austin", "baku", "bahrain", "catalunya",
            "hungaroring", "imola", "interlagos", "jeddah", "lusail", "madring",
            "marina_bay", "miami", "monaco", "montmelo", "monza", "redbullring",
            "shanghai", "silverstone", "spa", "suzuka", "vegas", "yasmarina", "zandvoort",
        )

        ids.forEach { id ->
            val asset = CircuitArtwork.forId(id)
            assertNotEquals("placeholder for $id", R.drawable.circuit_placeholder, asset.resourceId)
            assertTrue("asset should be tintable for $id", asset.tintable)
        }
    }

    @Test
    fun `known circuit id resolves to bundled artwork`() {
        val asset = CircuitArtwork.forId("bahrain")

        assertEquals(R.drawable.circuit_bahrain, asset.resourceId)
        assertTrue(asset.tintable)
    }

    @Test
    fun `unknown circuit id resolves to neutral placeholder`() {
        val asset = CircuitArtwork.forId("future_circuit")

        assertEquals(R.drawable.circuit_placeholder, asset.resourceId)
        assertNotEquals(R.drawable.circuit_bahrain, asset.resourceId)
        assertTrue(!asset.tintable)
    }
}
