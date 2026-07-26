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
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 2 at the use-case level: drive [GetRoundResultsUseCase] through a
 * MockEngine-backed [HttpClient]. The use case now hits Jolpica standard
 * `/ergast/f1/{year}/{round}/results.json` as the single source for race
 * results (the old f1api.dev `/{year}/{round}/race` hybrid merge is retired).
 *
 * Verifies:
 *  - 200 + valid Ergast envelope → Success(RoundResults) with the ordered
 *    results, the circuit from the inlined `Circuit` block, and `position`
 *    kept as a String (numeric finishers / "NC" for retirees).
 *  - 4xx/5xx → Failure with the expected status-coded message.
 *  - URL hits `/ergast/f1/{year}/{round}/results.json` (not f1api.dev).
 *  - `forceRefresh = true` adds `Cache-Control: no-cache`.
 */
class GetRoundResultsUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetRoundResultsUseCase = GetRoundResultsUseCase(
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
            defaultRequest { url(F1API_BASE) }
        }
    )

    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    // Truncated 2-row Ergast envelope — enough to prove the full-richness
    // mapping (status, grid, Time, FastestLap, Constructor, Circuit.Location,
    // Driver givenName/familyName/code/permanentNumber) wires through.
    private val SAMPLE_BODY = """
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
                { "number": "1", "position": "1", "positionText": "1", "points": "25",
                  "grid": "1", "laps": "57", "status": "Finished",
                  "Driver": { "driverId": "max_verstappen", "permanentNumber": "1", "code": "VER",
                              "givenName": "Max", "familyName": "Verstappen" },
                  "Constructor": { "constructorId": "red_bull", "name": "Red Bull Racing" },
                  "Time": { "millis": "5500000", "time": "1:31:44.000" },
                  "FastestLap": { "rank": "1", "lap": "57", "Time": { "time": "1:32.000" } } },
                { "number": "44", "position": "19", "positionText": "R", "points": "0",
                  "grid": "11", "laps": "15", "status": "Retired",
                  "Driver": { "driverId": "hamilton", "permanentNumber": "44", "code": "HAM",
                              "givenName": "Lewis", "familyName": "Hamilton" },
                  "Constructor": { "constructorId": "ferrari", "name": "Ferrari" } }
              ]
            }] }
        } }
    """.trimIndent()

    @Test
    fun `invoke returns Success RoundResults on 200`() = runTest {
        val out = useCase { jsonOk(SAMPLE_BODY) }.invoke(year = 2024, round = 1)
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val results = (out as Outcome.Success).data
        assertEquals(2024, results.year)
        assertEquals(1, results.round)
        assertEquals("Gulf Air Bahrain Grand Prix 2024", results.raceName)
        assertEquals("2024-03-02", results.date)
        assertEquals("15:00:00Z", results.time)
        assertEquals("bahrain", results.circuit.id)
        assertEquals("Sakhir", results.circuit.city)
        assertEquals(2, results.results.size)

        val winner = results.results[0]
        assertEquals("1", winner.position)
        assertEquals(25, winner.points)
        assertEquals("Max Verstappen", winner.driverName)
        assertEquals("VER", winner.driverShortName)
        assertEquals(1, winner.driverNumber)
        assertEquals("red_bull", winner.teamId)
        assertEquals("Finished", winner.status)
        assertEquals("1:31:44.000", winner.time)
        assertEquals("1:32.000", winner.fastLap)

        val retiree = results.results[1]
        assertEquals("NC", retiree.position)
        assertEquals("Retired", retiree.status)
        assertEquals("", retiree.time ?: "")
        assertEquals("", retiree.fastLap ?: "")
    }

    @Test
    fun `invoke hits the Jolpica results endpoint`() = runTest {
        val requestedPaths = mutableListOf<String>()
        val out = useCase { req ->
            requestedPaths += req.url.fullPath
            jsonOk(SAMPLE_BODY)
        }.invoke(year = 2024, round = 5)
        assertTrue(out is Outcome.Success)
        assertTrue(
            "Jolpica results request missing: $requestedPaths",
            "/ergast/f1/2024/5/results.json" in requestedPaths,
        )
    }

    @Test
    fun `invoke returns Failure on 4xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.NotFound) }.invoke(year = 2024, round = 99)
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals("Request failed (404)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `invoke returns Failure on 5xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.InternalServerError) }
            .invoke(year = 2024, round = 1)
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals("Server error (500)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `invoke with forceRefresh sends the no-cache header`() = runTest {
        var cacheControl: String? = null
        val out = useCase { req ->
            cacheControl = req.headers[HttpHeaders.CacheControl]
            jsonOk(SAMPLE_BODY)
        }.invoke(year = 2024, round = 1, forceRefresh = true)
        assertTrue(out is Outcome.Success)
        assertTrue(
            "expected no-cache header, was: $cacheControl",
            cacheControl?.contains("no-cache", ignoreCase = true) == true,
        )
    }
}