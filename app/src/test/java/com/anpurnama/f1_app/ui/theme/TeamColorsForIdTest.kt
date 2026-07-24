package com.anpurnama.f1_app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamColorsForIdTest {

    @Test
    fun `forId maps known constructor ids`() {
        assertEquals(Color(0xFFED1131), TeamColors.forId("ferrari"))
        assertEquals(Color(0xFF00D7B6), TeamColors.forId("mercedes"))
        assertEquals(Color(0xFF4781D7), TeamColors.forId("redbull"))
    }

    @Test
    fun `forId returns unspecified for an unknown constructor`() {
        assertEquals(Color.Unspecified, TeamColors.forId("new_constructor"))
    }
}
