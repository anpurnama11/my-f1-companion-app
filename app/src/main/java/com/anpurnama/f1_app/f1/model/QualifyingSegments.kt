package com.anpurnama.f1_app.f1.model

/** Segment tabs derived from one qualifying session result. */
enum class QualifyingSegment(
    val label: String,
    val shortLabel: String,
) {
    Q1("Qualifying 1", "Q1"),
    Q2("Qualifying 2", "Q2"),
    Q3("Qualifying 3", "Q3"),
}

data class QualifyingSegmentTab(
    val segment: QualifyingSegment,
    val rows: List<QualifyingSegmentResult>,
    val advancedCount: Int,
    val eliminatedCount: Int,
)

data class QualifyingSegmentResult(
    val segment: QualifyingSegment,
    val segmentPosition: Int,
    val time: String?,
    val eliminated: Boolean,
    val overallPosition: Int,
    val driverId: String,
    val driverName: String,
    val driverShortName: String?,
    val driverNumber: Int?,
    val teamId: String,
    val teamName: String,
)

fun List<QualifyingResult>.toQualifyingSegmentTabs(): List<QualifyingSegmentTab> = listOf(
    toSegmentTab(
        segment = QualifyingSegment.Q1,
        timeOf = QualifyingResult::q1,
        eliminatedBy = { it.q2 == null },
    ),
    toSegmentTab(
        segment = QualifyingSegment.Q2,
        timeOf = QualifyingResult::q2,
        eliminatedBy = { it.q3 == null },
    ),
    toSegmentTab(
        segment = QualifyingSegment.Q3,
        timeOf = QualifyingResult::q3,
        eliminatedBy = { false },
    ),
)

private fun List<QualifyingResult>.toSegmentTab(
    segment: QualifyingSegment,
    timeOf: (QualifyingResult) -> String?,
    eliminatedBy: (QualifyingResult) -> Boolean,
): QualifyingSegmentTab {
    val rows = asSequence()
        .mapIndexed { originalIndex, result -> result to originalIndex }
        .filter { (result, _) -> timeOf(result) != null }
        .sortedWith(
            compareBy<Pair<QualifyingResult, Int>>(
                { (result, _) -> parseQualifyingLapTimeMillis(timeOf(result)) == null },
                { (result, _) -> parseQualifyingLapTimeMillis(timeOf(result)) ?: Long.MAX_VALUE },
                { (_, originalIndex) -> originalIndex },
            ),
        )
        .mapIndexed { index, (result, _) ->
            QualifyingSegmentResult(
                segment = segment,
                segmentPosition = index + 1,
                time = timeOf(result),
                eliminated = eliminatedBy(result),
                overallPosition = result.gridPosition,
                driverId = result.driverId,
                driverName = result.driverName,
                driverShortName = result.driverShortName,
                driverNumber = result.driverNumber,
                teamId = result.teamId,
                teamName = result.teamName,
            )
        }
        .toList()
    return QualifyingSegmentTab(
        segment = segment,
        rows = rows,
        advancedCount = rows.count { !it.eliminated },
        eliminatedCount = rows.count { it.eliminated },
    )
}

internal fun parseQualifyingLapTimeMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val normalized = value.trim().replace(',', '.')
    val parts = normalized.split(':')
    return when (parts.size) {
        1 -> parseSecondsMillis(parts[0])
        2 -> {
            val minutes = parts[0].toLongOrNull() ?: return null
            val secondsMillis = parseSecondsMillis(parts[1]) ?: return null
            minutes * 60_000 + secondsMillis
        }
        3 -> {
            val minutes = parts[0].toLongOrNull() ?: return null
            val seconds = parts[1].toLongOrNull() ?: return null
            val millis = parts[2].padEnd(3, '0').take(3).toLongOrNull() ?: return null
            minutes * 60_000 + seconds * 1_000 + millis
        }
        else -> null
    }
}

private fun parseSecondsMillis(value: String): Long? {
    val parts = value.split('.')
    return when (parts.size) {
        1 -> parts[0].toLongOrNull()?.times(1_000)
        2 -> {
            val seconds = parts[0].toLongOrNull() ?: return null
            val millis = parts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return null
            seconds * 1_000 + millis
        }
        else -> null
    }
}
