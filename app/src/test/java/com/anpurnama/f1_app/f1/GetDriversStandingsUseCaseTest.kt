package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.JOLPICA_BASE
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

class GetDriversStandingsUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetDriversStandingsUseCase = GetDriversStandingsUseCase(
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
            defaultRequest { url(JOLPICA_BASE) }
        }
    )

    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    @Test
    fun `invoke returns Success list on 200`() = runTest {
        val body = """
            {
              "MRData": {
                "StandingsTable": {
                  "season": "2026",
                  "round": "11",
                  "StandingsLists": [{
                    "DriverStandings": [{
                      "position": "1",
                      "points": "204",
                      "wins": "6",
                      "Driver": { "driverId": "antonelli", "permanentNumber": "12", "code": "ANT",
                                  "givenName": "Andrea Kimi", "familyName": "Antonelli" },
                      "Constructors": [{ "constructorId": "mercedes", "name": "Mercedes" }]
                    }]
                  }]
                }
              }
            }
        """.trimIndent()
        val out = useCase { jsonOk(body) }.invoke()
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val standings = (out as Outcome.Success).data
        assertEquals(1, standings.size)
        assertEquals("antonelli", standings[0].driverId)
        assertEquals(1, standings[0].position)
    }

    @Test
    fun `invoke returns Failure on 4xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.NotFound) }.invoke()
        assertTrue(out is Outcome.Failure)
        assertEquals("Request failed (404)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `invoke returns Failure on 5xx`() = runTest {
        val out = useCase { respondError(HttpStatusCode.InternalServerError) }.invoke()
        assertTrue(out is Outcome.Failure)
        assertEquals("Server error (500)", (out as Outcome.Failure).errorMessage)
    }
}
