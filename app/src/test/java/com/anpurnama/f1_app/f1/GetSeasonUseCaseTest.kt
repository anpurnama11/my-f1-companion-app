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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 1 at the use-case level: drive [GetSeasonUseCase] through a
 * MockEngine-backed [HttpClient]. Confirms the use case's contract end-to-end:
 * - 200 + valid JSON → Outcome.Success(Season) with pre-computed aggregates.
 * - 4xx → Outcome.Failure with the ClientRequestException message.
 * - 5xx → Outcome.Failure with the ServerResponseException message.
 *
 * The MockEngine setup is the same one used in F1ApiTest: handler is a
 * MockRequestHandleScope extension, `respond` is the String overload, and
 * `expectSuccess = true` so 4xx/5xx throw before body deserialization.
 */
class GetSeasonUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetSeasonUseCase = GetSeasonUseCase(
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
    fun `invoke returns Success Season with pre-computed aggregates on 200`() = runTest {
        val body = """
            { "season": 2026,
              "races": [
                { "round": 1, "raceName": "Bahrain GP",
                  "circuit": { "circuitId": "bahrain", "circuitName": "Bahrain", "circuitLength": "5412km" },
                  "winner": { "driverId": "max_verstappen", "constructorId": "red_bull" },
                  "laps": 57 }
              ]
            }
        """.trimIndent()
        val out = useCase { jsonOk(body) }.invoke()
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val season = (out as Outcome.Success).data
        assertEquals(2026, season.year)
        assertEquals(1, season.completedGp)
        // Wire "5412km" = 5412 meters = 5.412 km (see SeasonAggregatesTest
        // for the unit contract).
        assertEquals(5.412, season.totalKm, 0.0001)
        assertEquals(57, season.totalLaps)
    }

    @Test
    fun `invoke returns Failure on 4xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.NotFound) }.invoke()
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals(
            "Request failed (404)",
            (out as Outcome.Failure).errorMessage,
        )
    }

    @Test
    fun `invoke returns Failure on 5xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.InternalServerError) }.invoke()
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals(
            "Server error (500)",
            (out as Outcome.Failure).errorMessage,
        )
    }
}
