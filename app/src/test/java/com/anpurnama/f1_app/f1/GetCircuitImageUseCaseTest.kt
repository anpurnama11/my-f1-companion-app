package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.OpenF1MeetingDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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

class GetCircuitImageUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun client(responseBody: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        val engine = MockEngine {
            respond(
                content = responseBody,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
    }

    @Test
    fun `returns first non-null circuit image url`() = runTest {
        val useCase = GetCircuitImageUseCase(
            client("""[
                {"meeting_key":1,"circuit_image":null},
                {"meeting_key":2,"circuit_image":"https://openf1.img/circuit.png"}
            ]""")
        )

        val result = useCase(2026, "Hungary")

        assertTrue(result is Outcome.Success)
        assertEquals("https://openf1.img/circuit.png", (result as Outcome.Success).data)
    }

    @Test
    fun `returns Success(null) when no image is available`() = runTest {
        val useCase = GetCircuitImageUseCase(
            client("""[{"meeting_key":1,"circuit_image":null}]""")
        )

        val result = useCase(2026, "Hungary")

        assertTrue(result is Outcome.Success)
        assertNull((result as Outcome.Success).data)
    }

    @Test
    fun `returns Success(null) when no meetings match`() = runTest {
        val useCase = GetCircuitImageUseCase(client("[]"))

        val result = useCase(2026, "Hungary")

        assertTrue(result is Outcome.Success)
        assertNull((result as Outcome.Success).data)
    }

    @Test
    fun `applies Silverstone country fallback when literal returns empty`() = runTest {
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += request.url.parameters["country_name"]!!
            val body = when (request.url.parameters["country_name"]) {
                "Great Britain" -> "[]"
                "United Kingdom" -> """[{"meeting_key":1,"circuit_image":"https://openf1.img/silverstone.png"}]"""
                else -> "[]"
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val useCase = GetCircuitImageUseCase(client)

        val result = useCase(2026, "Great Britain")

        assertEquals(listOf("Great Britain", "United Kingdom"), requests)
        assertEquals("https://openf1.img/silverstone.png", (result as Outcome.Success).data)
    }

    @Test
    fun `returns Failure on 4xx response`() = runTest {
        val useCase = GetCircuitImageUseCase(
            client("""{"detail":"Not found"}""", HttpStatusCode.NotFound)
        )

        val result = useCase(2026, "Hungary")

        assertTrue(result is Outcome.Failure)
    }
}
