package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.OPENF1_BASE
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 2 at the use-case level for the OpenF1 top-speed join.
 *
 * The use case must:
 *  1. Resolve the Qualifying `session_key` for `(country, year, qualyDate)`
 *     via `/v1/sessions` filtered by `country_name` + Qualifying-day
 *     match. The Qualifying day is f1api.dev's `schedule.qualy.date` —
 *     OpenF1's `date_start` is Qualifying day, which is 1 day before
 *     the race (or 2 days for sprint weekends). The date match is the
 *     unique disambiguator for multi-circuit countries (US 3, Italy 2,
 *     Spain 2 from 2026).
 *  2. Take `max(stSpeed)` over `/v1/laps?session_key=...`.
 *  3. Fall back to `F1API_TO_OPENF1_COUNTRY` when the literal returns 0
 *     (e.g. "Great Britain" → "United Kingdom" for Silverstone).
 *  4. Return `Outcome.Success(null)` (not Failure) when no session is
 *     resolvable — pre-2023, no Qualifying on the calendar, or both
 *     country lookups returning 0. §3 renders an empty cell.
 *  5. Map 4xx/5xx to `Outcome.Failure` per the existing convention.
 *
 * The harness uses `defaultRequest { url(OPENF1_BASE) }` so request paths
 * are the OpenF1 endpoints relative to the base.
 */
class GetCircuitTopSpeedUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetCircuitTopSpeedUseCase = GetCircuitTopSpeedUseCase(
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
            defaultRequest { url(OPENF1_BASE) }
        }
    )

    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    @Test
    fun `invoke returns Success null on no match (off-season or pre-2023)`() = runTest {
        val out = useCase { jsonOk("[]") }.invoke(
            circuitId = "imola", country = "Italy", year = 2022, qualyDate = "2022-04-23",
        )
        assertTrue("expected Success, was $out", out is Outcome.Success)
        assertNull((out as Outcome.Success).data)
    }

    @Test
    fun `invoke returns Failure on 4xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.NotFound) }.invoke(
            circuitId = "imola", country = "Italy", year = 2024, qualyDate = "2024-05-18",
        )
        assertTrue(out is Outcome.Failure)
        assertEquals("Request failed (404)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `invoke returns Failure on 5xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.InternalServerError) }.invoke(
            circuitId = "imola", country = "Italy", year = 2024, qualyDate = "2024-05-18",
        )
        assertTrue(out is Outcome.Failure)
        assertEquals("Server error (500)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `invoke picks the session whose qualy date matches the OpenF1 date_start`() = runTest {
        // Italy has two circuits in 2024 (Imola Qualifying 2024-05-18, Monza Qualifying 2024-08-31).
        // Both returned by the literal "Italy" filter; the Qualifying day picks the right one.
        val sessions = """
            [
              { "session_key": 9511, "session_name": "Qualifying",
                "date_start": "2024-05-18T14:00:00+00:00", "country_name": "Italy", "year": 2024 },
              { "session_key": 9586, "session_name": "Qualifying",
                "date_start": "2024-08-31T14:00:00+00:00", "country_name": "Italy", "year": 2024 }
            ]
        """.trimIndent()
        val laps = """
            [
              { "session_key": 9586, "driver_number": 1, "lap_number": 2, "st_speed": 306 },
              { "session_key": 9586, "driver_number": 4, "lap_number": 3, "st_speed": 318 },
              { "session_key": 9586, "driver_number": 16, "lap_number": 2, "st_speed": 322 }
            ]
        """.trimIndent()
        val out = useCase { req ->
            when {
                req.url.encodedPath.endsWith("/sessions") -> jsonOk(sessions)
                req.url.encodedPath.endsWith("/laps") -> jsonOk(laps)
                else -> error("unexpected ${req.url}")
            }
        }.invoke(
            circuitId = "monza", country = "Italy", year = 2024, qualyDate = "2024-08-31",
        )
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val top = (out as Outcome.Success).data
        assertNotNull(top)
        assertEquals("monza", top!!.circuitId)
        // session 9586 = Monza Qualifying → max of 306/318/322 = 322
        assertEquals(322, top.speedKph)
    }

    @Test
    fun `invoke applies the Great Britain to United Kingdom fallback on literal zero`() = runTest {
        // First call (literal "Great Britain") returns []. Second call
        // (fallback "United Kingdom") returns the Silverstone session.
        val silverstoneSession = """
            [
              { "session_key": 9500, "session_name": "Qualifying",
                "date_start": "2024-07-06T14:00:00+00:00", "country_name": "United Kingdom", "year": 2024 }
            ]
        """.trimIndent()
        val laps = """
            [
              { "session_key": 9500, "driver_number": 1, "lap_number": 9, "st_speed": 320 },
              { "session_key": 9500, "driver_number": 4, "lap_number": 11, "st_speed": 311 }
            ]
        """.trimIndent()
        val calls = mutableListOf<String>()
        val out = useCase { req ->
            calls += req.url.parameters["country_name"] ?: ""
            when {
                req.url.encodedPath.endsWith("/sessions") -> when (calls.last()) {
                    "Great Britain" -> jsonOk("[]")
                    "United Kingdom" -> jsonOk(silverstoneSession)
                    else -> error("unexpected country $calls")
                }
                req.url.encodedPath.endsWith("/laps") -> jsonOk(laps)
                else -> error("unexpected ${req.url}")
            }
        }.invoke(
            circuitId = "silverstone", country = "Great Britain", year = 2024, qualyDate = "2024-07-06",
        )
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val top = (out as Outcome.Success).data
        assertNotNull(top)
        assertEquals("silverstone", top!!.circuitId)
        assertEquals(320, top.speedKph)
        // Both literal + fallback were tried; order matters.
        assertEquals(listOf("Great Britain", "United Kingdom"), calls.take(2))
    }

    @Test
    fun `invoke returns Success null when both literal and fallback return zero sessions`() = runTest {
        val out = useCase { jsonOk("[]") }.invoke(
            circuitId = "bahrain", country = "Bahrain", year = 2018, qualyDate = "2018-04-07",
        )
        assertTrue("expected Success, was $out", out is Outcome.Success)
        assertNull((out as Outcome.Success).data)
    }

    @Test
    fun `invoke returns Success null when laps contain no stSpeed values`() = runTest {
        val sessions = """
            [
              { "session_key": 9700, "session_name": "Qualifying",
                "date_start": "2024-04-12T16:00:00+00:00", "country_name": "Bahrain", "year": 2024 }
            ]
        """.trimIndent()
        val laps = """
            [
              { "session_key": 9700, "driver_number": 1, "lap_number": 1, "st_speed": null },
              { "session_key": 9700, "driver_number": 1, "lap_number": 2, "st_speed": null }
            ]
        """.trimIndent()
        val out = useCase { req ->
            if (req.url.encodedPath.endsWith("/sessions")) jsonOk(sessions)
            else jsonOk(laps)
        }.invoke(
            circuitId = "bahrain", country = "Bahrain", year = 2024, qualyDate = "2024-04-12",
        )
        assertTrue(out is Outcome.Success)
        assertNull((out as Outcome.Success).data)
    }
}
