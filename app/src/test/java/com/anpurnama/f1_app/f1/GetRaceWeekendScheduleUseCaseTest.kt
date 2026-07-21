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
 * Rung 2 at the use-case level for the race-weekend schedule.
 *
 * The use case must:
 *  1. Hit `/v1/sessions?year=…&country_name=…` (no `session_name` filter).
 *  2. Map each `OpenF1SessionDto` to a `SessionTime` — parse `date_start`
 *     to `Instant`, label the session, drop cancelled ones.
 *  3. Sort sessions ascending by `start`.
 *  4. Fall back to `F1API_TO_OPENF1_COUNTRY` when the literal returns 0
 *     (e.g. "Great Britain" → "United Kingdom" for Silverstone).
 *  5. Return `Outcome.Success(null)` (not Failure) when no session is
 *     resolvable — pre-2023, off-season, both country lookups returning
 *     0. The §1 card renders an empty state.
 *  6. Map 4xx/5xx to `Outcome.Failure` per the existing convention.
 */
class GetRaceWeekendScheduleUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetRaceWeekendScheduleUseCase = GetRaceWeekendScheduleUseCase(
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
    fun `invoke returns sorted sessions on a happy path`() = runTest {
        val out = useCase { jsonOk(hugrBody) }.invoke(year = 2026, country = "Hungary")
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val schedule = (out as Outcome.Success).data
        assertNotNull(schedule)
        // 4 sessions: FP1, FP2, FP3, Quali, Race — Hungarian GP is a
        // non-sprint weekend. Order must be ascending by start.
        val labels = schedule!!.sessions.map { it.label }
        assertEquals(listOf("Practice 1", "Practice 2", "Practice 3", "Qualifying", "Race"), labels)
        // Short labels are the F1 broadcast form.
        assertEquals(listOf("FP1", "FP2", "FP3", "QUALI", "RACE"), schedule.sessions.map { it.shortLabel })
    }

    @Test
    fun `invoke drops cancelled sessions and unparseable dates`() = runTest {
        val out = useCase { jsonOk(hugrBodyWithCancelledAndMissing) }
            .invoke(year = 2026, country = "Hungary")
        assertTrue(out is Outcome.Success)
        val schedule = (out as Outcome.Success).data!!
        // The cancelled FP3 is dropped; the session with no date_start is
        // dropped; the remaining 3 (Race, Practice 1, Practice 2) surface.
        assertEquals(3, schedule.sessions.size)
        assertTrue(schedule.sessions.none { it.label == "Practice 3" })
        assertTrue(schedule.sessions.none { it.label == "Qualifying" })
    }

    @Test
    fun `invoke returns Success null when the literal country returns 0 sessions and no fallback exists`() = runTest {
        val out = useCase { jsonOk("[]") }.invoke(year = 2022, country = "Italy")
        assertTrue(out is Outcome.Success)
        assertNull((out as Outcome.Success).data)
    }

    @Test
    fun `invoke falls back to F1API_TO_OPENF1_COUNTRY on empty literal result`() = runTest {
        var callCount = 0
        val out = useCase { request ->
            callCount++
            // First call (literal "Great Britain") returns empty; second
            // call (fallback "United Kingdom") returns Silverstone's sessions.
            if (callCount == 1) jsonOk("[]") else jsonOk(silverstoneBody)
        }.invoke(year = 2026, country = "Great Britain")
        assertEquals(2, callCount)
        assertTrue(out is Outcome.Success)
        val schedule = (out as Outcome.Success).data
        assertNotNull(schedule)
        assertEquals(5, schedule!!.sessions.size)
    }

    @Test
    fun `invoke returns Failure on 4xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.NotFound) }
            .invoke(year = 2026, country = "Hungary")
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals("Request failed (404)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `invoke returns Failure on 5xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.InternalServerError) }
            .invoke(year = 2026, country = "Hungary")
        assertTrue(out is Outcome.Failure)
        assertEquals("Server error (500)", (out as Outcome.Failure).errorMessage)
    }

    // Test fixtures: a non-sprint weekend (Hungarian GP 2026) on OpenF1
    // format. `date_start` is ISO-8601 with offset (always +00:00 here).
    private val hugrBody = """
        [
          { "session_key": 9001, "session_name": "Race",
            "date_start": "2026-07-26T13:00:00+00:00",
            "country_name": "Hungary", "year": 2026, "is_cancelled": false },
          { "session_key": 9002, "session_name": "Practice 1",
            "date_start": "2026-07-24T11:30:00+00:00",
            "country_name": "Hungary", "year": 2026, "is_cancelled": false },
          { "session_key": 9003, "session_name": "Qualifying",
            "date_start": "2026-07-25T14:00:00+00:00",
            "country_name": "Hungary", "year": 2026, "is_cancelled": false },
          { "session_key": 9004, "session_name": "Practice 2",
            "date_start": "2026-07-24T15:00:00+00:00",
            "country_name": "Hungary", "year": 2026, "is_cancelled": false },
          { "session_key": 9005, "session_name": "Practice 3",
            "date_start": "2026-07-25T10:30:00+00:00",
            "country_name": "Hungary", "year": 2026, "is_cancelled": false }
        ]
    """.trimIndent()

    // Same weekend, but with a cancelled FP3 and a malformed date_start on
    // Qualifying — both should be dropped, leaving 4 sessions.
    private val hugrBodyWithCancelledAndMissing = """
        [
          { "session_key": 9001, "session_name": "Race",
            "date_start": "2026-07-26T13:00:00+00:00",
            "country_name": "Hungary", "year": 2026, "is_cancelled": false },
          { "session_key": 9002, "session_name": "Practice 1",
            "date_start": "2026-07-24T11:30:00+00:00",
            "country_name": "Hungary", "year": 2026, "is_cancelled": false },
          { "session_key": 9003, "session_name": "Qualifying",
            "date_start": null,
            "country_name": "Hungary", "year": 2026, "is_cancelled": false },
          { "session_key": 9004, "session_name": "Practice 2",
            "date_start": "2026-07-24T15:00:00+00:00",
            "country_name": "Hungary", "year": 2026, "is_cancelled": false },
          { "session_key": 9005, "session_name": "Practice 3",
            "date_start": "2026-07-25T10:30:00+00:00",
            "country_name": "Hungary", "year": 2026, "is_cancelled": true }
        ]
    """.trimIndent()

    // Silverstone (Great Britain) — used for the F1API_TO_OPENF1_COUNTRY
    // fallback test. Five sessions in calendar order to make the test
    // robust to the mapper's sort.
    private val silverstoneBody = """
        [
          { "session_key": 8001, "session_name": "Practice 1",
            "date_start": "2026-07-03T11:30:00+00:00",
            "country_name": "United Kingdom", "year": 2026, "is_cancelled": false },
          { "session_key": 8002, "session_name": "Practice 2",
            "date_start": "2026-07-03T15:00:00+00:00",
            "country_name": "United Kingdom", "year": 2026, "is_cancelled": false },
          { "session_key": 8003, "session_name": "Practice 3",
            "date_start": "2026-07-04T10:30:00+00:00",
            "country_name": "United Kingdom", "year": 2026, "is_cancelled": false },
          { "session_key": 8004, "session_name": "Qualifying",
            "date_start": "2026-07-04T14:00:00+00:00",
            "country_name": "United Kingdom", "year": 2026, "is_cancelled": false },
          { "session_key": 8005, "session_name": "Race",
            "date_start": "2026-07-05T13:00:00+00:00",
            "country_name": "United Kingdom", "year": 2026, "is_cancelled": false }
        ]
    """.trimIndent()
}
