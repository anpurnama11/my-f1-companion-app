package com.anpurnama.f1_app.f1.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Formats an API UTC session slot in the requested (device by default) zone. */
fun SessionSlot.toDeviceLocalLabel(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val local = toInstantOrNull()?.toLocalDateTime(timeZone) ?: return "Time unavailable"
    val day = local.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
    val month = local.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
    return "$day ${local.dayOfMonth} $month · ${"%02d".format(local.hour)}:${"%02d".format(local.minute)}"
}
