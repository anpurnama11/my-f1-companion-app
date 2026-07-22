package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.CircuitDto
import com.anpurnama.f1_app.f1.data.RaceDto
import com.anpurnama.f1_app.f1.data.SeasonResponseDto
import com.anpurnama.f1_app.f1.data.WinnerDto
import com.anpurnama.f1_app.f1.toSeason
import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonAggregatesTest {

    private fun race(
        round: Int,
        circuitId: String,
        circuitLength: String,
        laps: Int? = null,
        hasWinner: Boolean = true,
    ): RaceDto = RaceDto(
        round = round,
        raceName = "Race $round",
        circuit = CircuitDto(
            circuitId = circuitId,
            circuitName = "Circuit $circuitId",
            circuitLength = circuitLength,
        ),
        winner = if (hasWinner) WinnerDto(driverId = "d$round", constructorId = "t$round") else null,
        laps = laps,
    )

    @Test
    fun `completedGp counts races with a winner only`() {
        val dto = SeasonResponseDto(
            season = 2026,
            races = listOf(
                race(1, "bahrain", "5412km", laps = 57, hasWinner = true),
                race(2, "jeddah", "6275km", laps = 50, hasWinner = true),
                race(3, "albert_park", "5300km", laps = 58, hasWinner = true),
                race(4, "suzuka", "5807km", laps = 53, hasWinner = false), // upcoming
                race(5, "shanghai", "5451km", laps = 56, hasWinner = false), // upcoming
            ),
        )
        val season = dto.toSeason()
        assertEquals(3, season.completedGp)
    }

    @Test
    fun `totalKm sums digit-stripped circuitLength of completed races only`() {
        val dto = SeasonResponseDto(
            season = 2026,
            races = listOf(
                race(1, "bahrain", "5412km", laps = 57, hasWinner = true),
                race(2, "jeddah", "6.275km", laps = 50, hasWinner = true),  // dot stripped
                race(3, "albert_park", "5,300km", laps = 58, hasWinner = true),  // comma stripped
                race(4, "suzuka", "5,807", laps = 53, hasWinner = false),  // upcoming — must NOT count
            ),
        )
        val season = dto.toSeason()
        assertEquals(5412 + 6275 + 5300, season.totalKm)
    }

    @Test
    fun `totalLaps sums laps of completed races only`() {
        val dto = SeasonResponseDto(
            season = 2026,
            races = listOf(
                race(1, "bahrain", "5412km", laps = 57, hasWinner = true),
                race(2, "jeddah", "6275km", laps = 50, hasWinner = true),
                race(3, "albert_park", "5300km", laps = null, hasWinner = true), // no laps field
                race(4, "suzuka", "5807km", laps = 53, hasWinner = false),  // upcoming
            ),
        )
        val season = dto.toSeason()
        assertEquals(57 + 50, season.totalLaps)
    }

    @Test
    fun `progressPercent is completedGp over scheduled races`() {
        val dto = SeasonResponseDto(
            season = 2026,
            races = listOf(
                race(1, "a", "1km", hasWinner = true),
                race(2, "b", "1km", hasWinner = true),
                race(3, "c", "1km", hasWinner = false),
                race(4, "d", "1km", hasWinner = false),
            ),
        )
        val season = dto.toSeason()
        // 2 completed / 4 scheduled
        assertEquals(0.5f, season.progressPercent, 0.0001f)
    }

    @Test
    fun `progressPercent is 0 when no races are scheduled`() {
        val dto = SeasonResponseDto(season = 2026, races = emptyList())
        val season = dto.toSeason()
        assertEquals(0f, season.progressPercent, 0.0001f)
    }

    @Test
    fun `garbage circuitLength contributes 0 rather than throwing`() {
        // ponytail: defensive — defensive parse, don't crash the screen on dirty data.
        val dto = SeasonResponseDto(
            season = 2026,
            races = listOf(
                race(1, "x", "???", hasWinner = true),
                race(2, "y", "5412km", hasWinner = true),
            ),
        )
        val season = dto.toSeason()
        assertEquals(5412, season.totalKm)
    }

    @Test
    fun `race schedule is carried onto the domain Race when present`() {
        val dto = SeasonResponseDto(
            season = 2024,
            races = listOf(
                RaceDto(
                    round = 1,
                    raceName = "Bahrain GP",
                    circuit = CircuitDto(circuitId = "bahrain", circuitName = "Bahrain"),
                    schedule = com.anpurnama.f1_app.f1.data.RaceScheduleDto(
                        fp1 = com.anpurnama.f1_app.f1.data.SessionDto(date = "2024-02-29", time = "11:30:00Z"),
                        fp2 = com.anpurnama.f1_app.f1.data.SessionDto(date = "2024-02-29", time = "15:00:00Z"),
                        fp3 = com.anpurnama.f1_app.f1.data.SessionDto(date = "2024-03-01", time = "11:30:00Z"),
                        qualy = com.anpurnama.f1_app.f1.data.SessionDto(date = "2024-03-01", time = "15:00:00Z"),
                        race = com.anpurnama.f1_app.f1.data.SessionDto(date = "2024-03-02", time = "15:00:00Z"),
                    ),
                ),
            ),
        )
        val season = dto.toSeason()
        val schedule = season.races[0].schedule
        assertEquals("2024-02-29", schedule?.fp1?.date)
        assertEquals("15:00:00Z", schedule?.fp2?.time)
        assertEquals("2024-03-01", schedule?.fp3?.date)
        assertEquals("15:00:00Z", schedule?.qualy?.time)
        assertEquals("2024-03-02", schedule?.race?.date)
    }

    @Test
    fun `race schedule is null when every slot is absent`() {
        // DTO defaults to all-null when the endpoint doesn't carry it.
        val dto = SeasonResponseDto(
            season = 2024,
            races = listOf(race(1, "bahrain", "5412km", hasWinner = false)),
        )
        val season = dto.toSeason()
        assertEquals(null, season.races[0].schedule)
    }
}
