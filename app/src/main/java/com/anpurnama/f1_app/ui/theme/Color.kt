package com.anpurnama.f1_app.ui.theme

import androidx.compose.ui.graphics.Color

// Core semantic palette — dark-first F1 design system (boxbox-club reference).
// Source of hex truth; Theme.kt assembles these into darkColorScheme().
val F1Primary = Color(0xFFFF3301)
val F1Secondary = Color(0xFF125DF0)
val F1Tertiary = Color(0xFF583FF2)
val FLError = Color(0xFFFA1A24)

val OnPrimary = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFFFFFFFF)
val OnTertiary = Color(0xFFFFFFFF)
val OnError = Color(0xFFFFFFFF)

val Surface = Color(0xFF0D0D0D)
val SurfaceContainer = Color(0xFF111111)
val SurfaceContainerHigh = Color(0xFF191919)
val OnSurface = Color(0xFFFFFFFF)
val OnSurfaceVariant = Color(0xFFE1E1E1)
val Outline = Color(0xFF404040)
val OutlineVariant = Color(0xFF212121)

// F1 result-highlight accents — domain aliases over the core palette so a theme
// change flows through badges. Used by Dashboard / Round detail highlights.
val DriversChampionship = Color(0xFF2267DD)
val FastestLap = F1Tertiary
val PolePosition = DriversChampionship
val DriverOfDay = FLError

// Per-circuit brand colors. Use as accent backgrounds on dark surfaces; never as
// text on dark (too saturated for readability — per design do's/don'ts).
object Circuits {
    val AbuDhabi = Color(0xFF009A4C)
    val Australia = Color(0xFFFFB723)
    val Austria = Color(0xFFC92026)
    val Azerbaijan = Color(0xFF00AE65)
    val Bahrain = Color(0xFFB72C2E)
    val Belgium = Color(0xFFFFBC12)
    val Brazil = Color(0xFF009B3A)
    val Canada = Color(0xFFC92026)
    val China = Color(0xFFC92026)
    val Hungary = Color(0xFF008751)
    val Italy = Color(0xFF009246)
    val Japan = Color(0xFFCE1126)
    val Mexico = Color(0xFF016B48)
    val Monaco = Color(0xFFCE1126)
    val Netherlands = Color(0xFFF47501)
    val Qatar = Color(0xFF891438)
    val SaudiArabia = Color(0xFF006C35)
    val Singapore = Color(0xFFC92026)
    val Spain = Color(0xFFC60B1E)
    val Uk = Color(0xFF232C79)
    val UsaAustin = Color(0xFF27549E)
    val UsaLasVegas = Color(0xFF27549E)
    val UsaMiami = Color(0xFF27549E)
}

// Pirelli tyre compound colors: text + background pairs.
object Tyres {
    val Soft = Color(0xFFFA1A24)
    val SoftBg = Color(0xFFFED1D3)
    val Medium = Color(0xFFFFA800)
    val MediumBg = Color(0xFFFFEECC)
    val Hard = Color(0xFF000000)
    val HardBg = Color(0xFFCCCCCC)
    val Intermediate = Color(0xFF38D700)
    val IntermediateBg = Color(0xFFD7F7CC)
    val Wet = Color(0xFF058BF6)
    val WetBg = Color(0xFFCDE8FD)
    val Unknown = Color(0xFF919191)
    val UnknownBg = Color(0xFFE9E9E9)
}
