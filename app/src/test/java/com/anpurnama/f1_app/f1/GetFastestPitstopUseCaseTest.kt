package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.JOLPICA_BASE
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetFastestPitstopUseCaseTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = GetFastestPitstopUseCase(
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
    fun `selects the fastest positive duration and preserves driver id`() = runTest {
        val out = useCase {
            jsonOk(
                """
                {"MRData":{"RaceTable":{"Races":[{"PitStops":[
                  {"driverId":"verstappen","duration":"2.100"},
                  {"driverId":"leclerc","duration":"1.955"},
                  {"driverId":"norris","duration":"0"}
                ]}]}}}
                """.trimIndent(),
            )
        }.invoke(2026, 1)

        assertTrue(out is Outcome.Success)
        val stop = (out as Outcome.Success).data
        assertEquals("leclerc", stop?.driverId)
        assertEquals(1.955, stop?.durationSeconds ?: 0.0, 0.0)
    }

    @Test
    fun `malformed and non-positive durations are ignored`() = runTest {
        val out = useCase {
            jsonOk(
                """
                {"MRData":{"RaceTable":{"Races":[{"PitStops":[
                  {"driverId":"verstappen","duration":"unknown"},
                  {"driverId":"leclerc","duration":"-1"},
                  {"driverId":"norris","duration":null}
                ]}]}}}
                """.trimIndent(),
            )
        }.invoke(2026, 1)

        assertTrue(out is Outcome.Success)
        assertNull((out as Outcome.Success).data)
    }

    @Test
    fun `empty race envelope returns successful null`() = runTest {
        val out = useCase { jsonOk("{\"MRData\":{\"RaceTable\":{\"Races\":[]}}}") }
            .invoke(2026, 1)

        assertEquals(Outcome.Success(null), out)
    }

    @Test
    fun `http failure becomes a data failure`() = runTest {
        val out = useCase {
            respond(
                content = "server unavailable",
                status = HttpStatusCode.ServiceUnavailable,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
        }.invoke(2026, 1)

        assertTrue(out is Outcome.Failure)
    }
}
