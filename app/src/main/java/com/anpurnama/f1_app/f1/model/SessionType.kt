package com.anpurnama.f1_app.f1.model

import kotlinx.serialization.Serializable

/** Session types exposed by a race weekend. */
@Serializable
enum class SessionType(
    val label: String,
    val shortLabel: String,
) {
    FP1("Practice 1", "FP1"),
    FP2("Practice 2", "FP2"),
    FP3("Practice 3", "FP3"),
    SprintQuali("Sprint Qualifying", "SQ"),
    Sprint("Sprint", "SPRINT"),
    Quali("Qualifying", "QUALI"),
    Race("Race", "RACE"),
}

enum class RoundMode { Upcoming, Past }
