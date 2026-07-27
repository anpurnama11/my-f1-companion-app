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
    val China = Color(0xFFC92026)
    val Hungary = Color(0xFF008751)
    val Italy = Color(0xFF009246)
    val Japan = Color(0xFFCE1126)
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

    /**
     * Neutral brand-accent fallback for any `circuitId` not in the
     * palette (e.g. a brand-new circuit added mid-season before the
     * design tokens catch up). A muted neutral beats picking a random
     * circuit color — that's a worse failure mode (lying about brand
     * identity). `private` so the only public surface is [forId] — the
     * fallback isn't a "real" circuit color callers should reference.
     */
    private val Neutral = Color(0xFF3A3A3A)

    /**
     * f1api.dev's `circuitId` is a snake/kebab slug (e.g. "hungaroring",
     * "usa_austin"). This maps it to the matching brand color; unknown
     * ids return the neutral fallback so the §3 card always renders.
     */
    fun forId(circuitId: String): Color = when (circuitId) {
        "abudhabi" -> AbuDhabi
        "albert_park" -> Australia
        "austin" -> UsaAustin
        "azerbaijan" -> Azerbaijan
        "bahrain" -> Bahrain
        "baku" -> Azerbaijan
        "catalunya" -> Spain
        "hungaroring" -> Hungary
        "imola" -> Italy
        "interlagos" -> Brazil
        "jeddah" -> SaudiArabia
        "lusail" -> Qatar
        "madring" -> Spain
        "marina_bay" -> Singapore
        "miami" -> UsaMiami
        "monaco" -> Monaco
        "montmelo" -> Spain
        "monza" -> Italy
        "redbullring" -> Austria
        "shanghai" -> China
        "silverstone" -> Uk
        "spa" -> Belgium
        "suzuka" -> Japan
        "vegas" -> UsaLasVegas
        "yasmarina" -> AbuDhabi
        "zandvoort" -> Netherlands
        else -> Neutral
    }
}
