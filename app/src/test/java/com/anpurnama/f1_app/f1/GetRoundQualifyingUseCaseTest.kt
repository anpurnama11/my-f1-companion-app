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
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 2 at the use-case level: drive [GetRoundQualifyingUseCase] through a
 * MockEngine-backed [HttpClient]. The use case now hits Jolpica standard
 * `/ergast/f1/{year}/{round}/qualifying.json` as the single source for
 * qualifying results (the old f1api.dev `/{year}/{round}/qualy` fetch is retired).
 *
 * Verifies:
 *  - 200 + valid Ergast envelope → Success(RoundQualifying) with the ordered
 *    results, full Driver richness (givenName/familyName/code/permanentNumber),
 *    the per-row Constructor, and the circuit from the inlined `Circuit`/
 *    `Location` block.
 *  - 4xx/5xx → Failure with the expected status-coded message.
 *  - URL hits `/ergast/f1/{year}/{round}/qualifying.json` (not f1api.dev).
 *  - `forceRefresh = true` adds `Cache-Control: no-cache`.
 *  - Q1-only knockout is parsed (Q2/Q3 null in source → null in model).
 */
class GetRoundQualifyingUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetRoundQualifyingUseCase = GetRoundQualifyingUseCase(
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

    // Truncated 2-row Ergast qualifying envelope — enough to prove the full-
    // richness mapping (Constructor on every row, Driver givenName/familyName/
    // code/permanentNumber, Circuit/Location, Q1-only knockout) wires through.
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
              "QualifyingResults": [
                { "number": "1", "position": "1",
                  "Driver": { "driverId": "max_verstappen", "permanentNumber": "1", "code": "VER",
                              "givenName": "Max", "familyName": "Verstappen" },
                  "Constructor": { "constructorId": "red_bull", "name": "Red Bull Racing" },
                  "Q1": "1:30.031", "Q2": "1:29.374", "Q3": "1:29.179" },
                { "number": "2", "position": "20",
                  "Driver": { "driverId": "sargeant", "permanentNumber": "2", "code": "SAR",
                              "givenName": "Logan", "familyName": "Sargeant" },
                  "Constructor": { "constructorId": "williams", "name": "Williams Racing" },
                  "Q1": "1:30.770", "Q2": null, "Q3": null }
              ]
            }] }
        } }
    """.trimIndent()

    @Test
    fun `invoke returns Success RoundQualifying on 200`() = runTest {
        val out = useCase { jsonOk(SAMPLE_BODY) }.invoke(year = 2024, round = 1)
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val qualy = (out as Outcome.Success).data
        assertEquals(2024, qualy.year)
        assertEquals(1, qualy.round)
        assertEquals("Gulf Air Bahrain Grand Prix 2024", qualy.raceName)
        // Ergast qualifying has no separate quali date/time — the race's
        // date/time round through (unused by the UI).
        assertEquals("2024-03-02", qualy.qualyDate)
        assertEquals("15:00:00Z", qualy.qualyTime)
        assertEquals("bahrain", qualy.circuit.id)
        assertEquals("Sakhir", qualy.circuit.city)
        assertEquals(2, qualy.results.size)

        val pole = qualy.results[0]
        assertEquals(1, pole.gridPosition)
        assertEquals("1:30.031", pole.q1)
        assertEquals("1:29.374", pole.q2)
        assertEquals("1:29.179", pole.q3)
        assertEquals("max_verstappen", pole.driverId)
        assertEquals("Max Verstappen", pole.driverName)
        assertEquals("VER", pole.driverShortName)
        assertEquals(1, pole.driverNumber)
        assertEquals("red_bull", pole.teamId)
        assertEquals("Red Bull Racing", pole.teamName)

        val q1Knockout = qualy.results[1]
        assertEquals(20, q1Knockout.gridPosition)
        assertEquals("1:30.770", q1Knockout.q1)
        assertNull(q1Knockout.q2)
        assertNull(q1Knockout.q3)
    }

    @Test
    fun `invoke hits the Jolpica qualifying endpoint and never the f1api qualy route`() = runTest {
        val requestedPaths = mutableListOf<String>()
        val out = useCase { req ->
            requestedPaths += req.url.fullPath
            jsonOk(SAMPLE_BODY)
        }.invoke(year = 2024, round = 5)
        assertTrue(out is Outcome.Success)
        // Single fetch, single Jolpica standard qualifying path — NOT the
        // retired f1api.dev `/{year}/{round}/qualy` route.
        assertEquals(listOf("/ergast/f1/2024/5/qualifying.json"), requestedPaths)
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