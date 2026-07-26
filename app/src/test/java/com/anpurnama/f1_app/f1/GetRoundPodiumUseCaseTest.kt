package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.F1API_BASE
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 2 at the use-case level for [GetRoundPodiumUseCase].
 *
 * Contract (per ticket 03 spec + lode/wayfinder/f1app/past-list.md):
 *  - **No extra network call.** The podium use case composes
 *    [GetRoundResultsUseCase] and slices `results[0..2]`. The
 *    HttpCache is the only reason the Past list and the Round
 *    drilldown don't double-fetch the same round.
 *  - Returns [RoundPodium] with `topThree: List<RoundResult>` (always
 *    exactly 3 when the source has 3+ rows, may be shorter for very
 *    short grids — a partial podium is still a podium, never a
 *    failure).
 *  - Failure path: same as the underlying Jolpica `/results.json` call.
 *    The use case does not paper over a 4xx/5xx with a "fewer than 3" success.
 */
class GetRoundPodiumUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetRoundPodiumUseCase = GetRoundPodiumUseCase(
        getRoundResults = GetRoundResultsUseCase(
            HttpClient(MockEngine(handler)) {
                expectSuccess = true
                install(ContentNegotiation) { json(json) }
                defaultRequest { url(F1API_BASE) }
            }
        )
    )

    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private val FULL_GRID_BODY = """
        { "MRData": { "RaceTable": {
            "season": "2024", "round": "1",
            "Races": [{
              "season": "2024", "round": "1",
              "raceName": "Gulf Air Bahrain Grand Prix 2024",
              "date": "2024-03-02", "time": "15:00:00Z",
              "Circuit": {
                "circuitId": "bahrain", "circuitName": "Bahrain International Circuit",
                "Location": { "locality": "Sakhir", "country": "Bahrain" }
              },
              "Results": [
                { "number": "1", "position": "1", "positionText": "1", "points": "26",
                  "grid": "1", "laps": "57", "status": "Finished",
                  "Driver": { "driverId": "max_verstappen", "permanentNumber": "1", "code": "VER",
                              "givenName": "Max", "familyName": "Verstappen" },
                  "Constructor": { "constructorId": "red_bull", "name": "Red Bull Racing" },
                  "Time": { "time": "1:31:44.000" } },
                { "number": "11", "position": "2", "positionText": "2", "points": "18",
                  "grid": "5", "laps": "57", "status": "Finished",
                  "Driver": { "driverId": "perez", "permanentNumber": "11", "code": "PER",
                              "givenName": "Sergio", "familyName": "Pérez" },
                  "Constructor": { "constructorId": "red_bull", "name": "Red Bull Racing" },
                  "Time": { "time": "+22.457" } },
                { "number": "55", "position": "3", "positionText": "3", "points": "15",
                  "grid": "4", "laps": "57", "status": "Finished",
                  "Driver": { "driverId": "sainz", "permanentNumber": "55", "code": "SAI",
                              "givenName": "Carlos", "familyName": "Sainz" },
                  "Constructor": { "constructorId": "ferrari", "name": "Ferrari" },
                  "Time": { "time": "+25.110" } },
                { "number": "16", "position": "4", "positionText": "4", "points": "12",
                  "grid": "2", "laps": "57", "status": "Finished",
                  "Driver": { "driverId": "leclerc", "permanentNumber": "16", "code": "LEC",
                              "givenName": "Charles", "familyName": "Leclerc" },
                  "Constructor": { "constructorId": "ferrari", "name": "Ferrari" },
                  "Time": { "time": "+39.669" } }
              ]
            }] }
        } }
    """.trimIndent()

    @Test
    fun `invoke returns the top 3 drivers from a full grid`() = runTest {
        val out = useCase { jsonOk(FULL_GRID_BODY) }.invoke(year = 2024, round = 1)
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val podium = (out as Outcome.Success).data
        assertEquals(3, podium.topThree.size)
        assertEquals("1", podium.topThree[0].position)
        assertEquals("max_verstappen", podium.topThree[0].driverId)
        assertEquals("Max Verstappen", podium.topThree[0].driverName)
        assertEquals("2", podium.topThree[1].position)
        assertEquals("perez", podium.topThree[1].driverId)
        assertEquals("3", podium.topThree[2].position)
        assertEquals("sainz", podium.topThree[2].driverId)
        // P4 (leclerc) must NOT be in the podium.
        assertTrue(podium.topThree.none { it.driverId == "leclerc" })
    }

    @Test
    fun `invoke returns fewer than 3 when the grid is short (partial podium)`() = runTest {
        val shortBody = """
            { "MRData": { "RaceTable": {
                "season": "2024", "round": "1",
                "Races": [{
                  "season": "2024", "round": "1",
                  "raceName": "Bahrain GP",
                  "Circuit": { "circuitId": "bahrain" },
                  "Results": [
                    { "number": "27", "position": "1", "positionText": "1", "points": "25",
                      "grid": "1", "laps": "57", "status": "Finished",
                      "Driver": { "driverId": "hulkenberg", "code": "HUL",
                                  "givenName": "Nico", "familyName": "Hülkenberg" },
                      "Constructor": { "constructorId": "haas", "name": "Haas F1 Team" },
                      "Time": { "time": "1:31:44.000" } }
                  ]
                }] }
            } }
        """.trimIndent()
        val out = useCase { jsonOk(shortBody) }.invoke(year = 2024, round = 1)
        assertTrue(out is Outcome.Success)
        val podium = (out as Outcome.Success).data
        assertEquals(1, podium.topThree.size)
        assertEquals("hulkenberg", podium.topThree[0].driverId)
    }

    @Test
    fun `invoke returns Failure on 4xx (no partial-success on network error)`() = runTest {
        val out = useCase { respondError(HttpStatusCode.NotFound) }.invoke(year = 2024, round = 99)
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals("Request failed (404)", (out as Outcome.Failure).errorMessage)
    }
}
