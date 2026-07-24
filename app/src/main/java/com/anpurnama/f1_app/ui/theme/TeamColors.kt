package com.anpurnama.f1_app.ui.theme

import androidx.compose.ui.graphics.Color

/** Stable v1 constructor accents; update once per livery season. */
object TeamColors {
    fun forId(teamId: String): Color = when (teamId) {
        "ferrari" -> Color(0xFFED1131)
        "mercedes" -> Color(0xFF00D7B6)
        "red_bull", "redbull" -> Color(0xFF4781D7)
        "mclaren" -> Color(0xFFF47600)
        "aston_martin" -> Color(0xFF229971)
        "alpine" -> Color(0xFF00A1E8)
        "williams" -> Color(0xFF1868DB)
        "rb", "racing_bulls" -> Color(0xFF6C98FF)
        "sauber", "kick_sauber", "audi" -> Color(0xFF01C00E)
        "haas" -> Color(0xFF9C9FA2)
        else -> Color.Unspecified
    }
}
