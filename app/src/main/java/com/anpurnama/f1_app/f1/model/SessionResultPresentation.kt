package com.anpurnama.f1_app.f1.model

/** Pure display classification for Race and Sprint result rows. */
fun RoundResult.displayGrid(): String = if (grid == "0") "PL" else grid.ifBlank { "—" }

fun RoundResult.displayStatusOrTime(): String = when {
    status.isDns() -> "DNS"
    status.isDnf() -> "DNF"
    else -> time ?: "—"
}

/** Positive means places gained; null means no reliable arrow can be shown. */
fun RoundResult.positionChange(): Int? {
    if (grid == "0") return null
    val gridPosition = grid.toIntOrNull() ?: return null
    val finishingPosition = position.toIntOrNull() ?: return null
    return gridPosition - finishingPosition
}

/** Joins the optional pit-stop card to the race result by canonical driver ID. */
fun SessionResult.driverForPitstop(stop: FastestPitstop): RoundResult? =
    raceResults.firstOrNull { it.driverId == stop.driverId }

private fun String?.isDns(): Boolean {
    val normalized = orEmpty().lowercase().replace('_', ' ')
    return normalized.contains("did not start") || normalized.contains("not started") || normalized == "dns"
}

private fun String?.isDnf(): Boolean {
    val normalized = orEmpty().lowercase().replace('_', ' ')
    return normalized.contains("retired") || normalized.contains("not classified") || normalized == "dnf"
}
