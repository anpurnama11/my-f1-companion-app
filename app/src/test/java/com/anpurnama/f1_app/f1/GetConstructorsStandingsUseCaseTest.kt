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

class GetConstructorsStandingsUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetConstructorsStandingsUseCase = GetConstructorsStandingsUseCase(
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
    fun `invoke returns Success list on 200`() = runTest {
        val body = """
            { "season": 2026,
              "constructorschampionship": [
                { "teamId": "mercedes", "position": 1, "points": 358, "wins": 8,
                  "team": { "teamName": "Mercedes Formula 1 Team", "country": "Germany" } }
              ]
            }
        """.trimIndent()
        val out = useCase { jsonOk(body) }.invoke()
        assertTrue(out is Outcome.Success)
        val standings = (out as Outcome.Success).data
        assertEquals(1, standings.size)
        assertEquals("mercedes", standings[0].teamId)
        assertEquals(1, standings[0].position)
        assertEquals("Mercedes Formula 1 Team", standings[0].teamName)
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
