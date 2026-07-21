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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 2 at the use-case level: drive [GetNextRaceUseCase] through a
 * MockEngine-backed [HttpClient]. Same harness as `GetSeasonUseCaseTest`
 * — `expectSuccess = true`, `respond` String overload, `defaultRequest`
 * `F1API_BASE` for relative path resolution.
 *
 * Verifies:
 *  - 200 + valid envelope → Success(NextRace) with the inlined circuit
 *    country + race date (the OpenF1 join keys).
 *  - 200 + empty `race` array → Success(null) (off-season is a success
 *    with no payload; the screen renders the empty state).
 *  - 4xx/5xx → Failure with the expected status-coded message.
 *  - `forceRefresh = true` adds `Cache-Control: no-cache`.
 */
class GetNextRaceUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetNextRaceUseCase = GetNextRaceUseCase(
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

    @Test
    fun `invoke returns Success NextRace on 200`() = runTest {
        val body = """
            { "season": 2026, "round": 11,
              "race": [{
                "raceId": "hungarian2026",
                "raceName": "Formula 1 AWS Hungarian Grand Prix 2026",
                "round": 11, "laps": 70,
                "circuit": {
                  "circuitId": "hungaroring", "circuitName": "Hungaroring",
                  "country": "Hungary", "city": "Mogyorod",
                  "circuitLength": "4381km", "corners": 14
                },
                "schedule": {
                  "race":  { "date": "2026-07-26", "time": "13:00:00Z" },
                  "qualy": { "date": "2026-07-25", "time": "14:00:00Z" }
                }
              }]
            }
        """.trimIndent()
        val out = useCase { jsonOk(body) }.invoke()
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val next = (out as Outcome.Success).data
        assertNotNull(next)
        assertEquals(11, next!!.round)
        assertEquals("Hungaroring", next.circuit.name)
        assertEquals("Hungary", next.circuit.country)
        assertEquals("2026-07-26", next.raceDate)
        assertEquals("2026-07-25", next.qualyDate)
    }

    @Test
    fun `invoke returns Success null on an empty race list (off-season)`() = runTest {
        val out = useCase { jsonOk("""{"season":2026,"round":0,"race":[]}""") }.invoke()
        assertTrue("expected Success, was $out", out is Outcome.Success)
        assertNull((out as Outcome.Success).data)
    }

    @Test
    fun `invoke returns Failure on 4xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.NotFound) }.invoke()
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals("Request failed (404)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `invoke returns Failure on 5xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.InternalServerError) }.invoke()
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals("Server error (500)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `invoke with forceRefresh sends the no-cache header`() = runTest {
        var cacheControl: String? = null
        val out = useCase { req ->
            cacheControl = req.headers[HttpHeaders.CacheControl]
            jsonOk("""{"season":2026,"round":0,"race":[]}""")
        }.invoke(forceRefresh = true)
        assertTrue(out is Outcome.Success)
        assertTrue(
            "expected no-cache header, was: $cacheControl",
            cacheControl?.contains("no-cache", ignoreCase = true) == true,
        )
    }

    @Test
    fun `invoke hits the expected URL`() = runTest {
        var requestedPath: String? = null
        val out = useCase { req ->
            requestedPath = req.url.fullPath
            jsonOk("""{"season":2026,"round":0,"race":[]}""")
        }.invoke()
        assertTrue(out is Outcome.Success)
        assertEquals("/api/current/next", requestedPath)
    }
}
