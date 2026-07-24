package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.JolpicaRaceResultsResponseDto
import com.anpurnama.f1_app.f1.data.RoundResultsResponseDto
import org.junit.Assert.assertEquals
import org.junit.Test

class HybridRoundResultsMapperTest {
    @Test
    fun `authority merge uses driver number for status and grid`() {
        val f1 = RoundResultsResponseDto(
            season = 2026,
            races = RoundResultsResponseDto.RacesDto(
                round = "1",
                raceName = "GP",
                circuit = listOf(RoundResultsResponseDto.RacesDto.CircuitDto(circuitId = "x")),
                results = listOf(RoundResultsResponseDto.RacesDto.ResultDto(
                    position = "NC", grid = "not available", fastLap = "1:40.000",
                    driver = RoundResultsResponseDto.RacesDto.ResultDto.DriverDto(
                        driverId = "driver", number = 23, shortName = "DRV", name = "A", surname = "Driver",
                    ),
                )),
            ),
        )
        val authority = JolpicaRaceResultsResponseDto(
            mrData = JolpicaRaceResultsResponseDto.MrDataDto(
                raceTable = JolpicaRaceResultsResponseDto.RaceTableDto(
                    races = listOf(JolpicaRaceResultsResponseDto.RaceDto(
                        results = listOf(JolpicaRaceResultsResponseDto.ResultDto(
                            number = "23", grid = "0", status = "Did not start",
                            driver = JolpicaRaceResultsResponseDto.DriverDto(driverId = "driver"),
                        )),
                    )),
                ),
            ),
        )

        val result = f1.toRoundResults(authority).results.single()
        assertEquals("0", result.grid)
        assertEquals("Did not start", result.status)
        assertEquals("1:40.000", result.fastLap)
    }
}
