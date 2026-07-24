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
 * Rung 2 at the use-case level: drive [GetRoundResultsUseCase] through
 * a MockEngine-backed [HttpClient]. Same harness as the other use case
 * tests.
 *
 * Verifies:
 *  - 200 + valid envelope → Success(RoundResults) with the ordered
 *    results, the inlined circuit from the one-element array, and
 *    `position` kept as a String.
 *  - 4xx/5xx → Failure with the expected status-coded message.
 *  - URL hits `/{year}/{round}/race` (not `/current`, etc.).
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

    private val SAMPLE_BODY = """
        { "season": 2024,
          "races": {
            "round": "1", "date": "2024-03-02", "time": "15:00:00Z",
            "raceId": "bahrein2024",
            "raceName": "Gulf Air Bahrain Grand Prix 2024",
            "circuit": [{
              "circuitId": "bahrain", "circuitName": "Bahrain International Circuit",
              "country": "Bahrain", "city": "Sakhir",
              "circuitLength": "5412km", "corners": 15
            }],
            "results": [
              { "position": "1", "points": 26, "grid": "1", "time": "1:31:44",
                "driver": { "driverId": "maxverstappen", "number": 33, "shortName": "VER",
                            "name": "Max", "surname": "Verstappen" },
                "team": { "teamId": "redbull", "teamName": "Red Bull Racing" } },
              { "position": "2", "points": 18, "grid": "5", "time": "+22.457",
                "driver": { "driverId": "perez", "number": 11, "shortName": "PER",
                            "name": "Sergio", "surname": "Pérez" },
                "team": { "teamId": "redbull", "teamName": "Red Bull Racing" } }
            ]
          }
        }
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
        assertEquals(2, results.results.size)
        assertEquals("1", results.results[0].position)
        assertEquals("Max Verstappen", results.results[0].driverName)
    }

    @Test
    fun `invoke hits the expected URL`() = runTest {
        val requestedPaths = mutableListOf<String>()
        val out = useCase { req ->
            requestedPaths += req.url.fullPath
            jsonOk(SAMPLE_BODY)
        }.invoke(year = 2024, round = 5)
        assertTrue(out is Outcome.Success)
        assertTrue("f1api race request missing: $requestedPaths", "/api/2024/5/race" in requestedPaths)
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
