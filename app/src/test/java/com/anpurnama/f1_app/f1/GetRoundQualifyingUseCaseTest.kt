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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rung 2 at the use-case level for [GetRoundQualifyingUseCase].
 *
 * Verifies:
 *  - 200 + valid envelope → Success(RoundQualifying) with the
 *    ordered results and the single `circuit` object (NOT a list).
 *  - 4xx/5xx → Failure with the expected status-coded message.
 *  - URL hits `/{year}/{round}/qualy`.
 *  - `forceRefresh = true` adds `Cache-Control: no-cache`.
 *  - q1-only knockout is parsed (q2/q3 null in source → null in model).
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

    private val SAMPLE_BODY = """
        { "season": 2024,
          "races": {
            "round": "1", "qualyDate": "2024-03-01", "qualyTime": "16:00:00Z",
            "raceId": "bahrein2024",
            "raceName": "Gulf Air Bahrain Grand Prix 2024",
            "circuit": {
              "circuitId": "bahrain", "circuitName": "Bahrain International Circuit",
              "country": "Bahrain", "city": "Sakhir",
              "circuitLength": "5412km", "corners": 15
            },
            "qualyResults": [
              { "classificationId": 1, "driverId": "maxverstappen", "teamId": "redbull",
                "q1": "1:30.031", "q2": "1:29.374", "q3": "1:29.179", "gridPosition": 1,
                "driver": { "driverId": "maxverstappen", "number": 33, "shortName": "VER",
                            "name": "Max", "surname": "Verstappen" },
                "team": { "teamId": "redbull", "teamName": "Red Bull Racing" } },
              { "classificationId": 17, "driverId": "sargeant", "teamId": "williams",
                "q1": "1:30.770", "q2": null, "q3": null, "gridPosition": 18,
                "driver": { "driverId": "sargeant", "number": 2, "shortName": "SAR",
                            "name": "Logan", "surname": "Sargeant" },
                "team": { "teamId": "williams", "teamName": "Williams Racing" } }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `invoke returns Success RoundQualifying on 200`() = runTest {
        val out = useCase { jsonOk(SAMPLE_BODY) }.invoke(year = 2024, round = 1)
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val qualy = (out as Outcome.Success).data
        assertEquals(2024, qualy.year)
        assertEquals(1, qualy.round)
        assertEquals("Gulf Air Bahrain Grand Prix 2024", qualy.raceName)
        assertEquals("2024-03-01", qualy.qualyDate)
        assertEquals("16:00:00Z", qualy.qualyTime)
        assertEquals("bahrain", qualy.circuit.id)
        assertEquals(2, qualy.results.size)
        assertEquals(1, qualy.results[0].gridPosition)
        assertEquals("1:29.179", qualy.results[0].q3)
        assertNull(qualy.results[1].q2)
        assertNull(qualy.results[1].q3)
    }

    @Test
    fun `invoke hits the expected URL`() = runTest {
        var requestedPath: String? = null
        val out = useCase { req ->
            requestedPath = req.url.fullPath
            jsonOk(SAMPLE_BODY)
        }.invoke(year = 2024, round = 5)
        assertTrue(out is Outcome.Success)
        assertEquals("/api/2024/5/qualy", requestedPath)
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
