package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.F1API_BASE
import com.anpurnama.f1_app.f1.data.getCurrent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class F1ApiTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Ktor 3.x: handler is a MockRequestHandleScope.() -> HttpResponseData lambda
    // with the request as a parameter. `respond` / `respondError` are extensions
    // on the scope, so they must be called on the scope, not as plain functions.
    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        defaultRequest { url(F1API_BASE) }
    }

    // Use the String overload of respond (Ktor wraps it itself). Wrapping in
    // ByteReadChannel up-front confuses the body deserializer — the engine
    // re-wraps the channel and ContentNegotiation sees a SourceByteReadChannel
    // it can't match, throwing NoTransformationFoundException.
    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    @Test
    fun `getCurrent hits the expected URL and decodes a 200 envelope`() = runTest {
        val body = """
            { "season": 2026,
              "races": [
                { "round": 1, "raceName": "Bahrain GP",
                  "circuit": { "circuitId": "bahrain", "circuitName": "Bahrain", "circuitLength": "5412km" },
                  "winner": { "driverId": "max_verstappen", "constructorId": "red_bull" },
                  "laps": 57 },
                { "round": 2, "raceName": "Saudi Arabian GP",
                  "circuit": { "circuitId": "jeddah", "circuitName": "Jeddah", "circuitLength": "6275km" } }
              ]
            }
        """.trimIndent()
        var requestedPath: String? = null
        val client = client { req ->
            requestedPath = req.url.fullPath
            jsonOk(body)
        }

        val dto = client.getCurrent()
        assertEquals("/api/current", requestedPath)
        assertEquals(2026, dto.season)
        assertEquals(2, dto.races.size)
        assertEquals("max_verstappen", dto.races[0].winner?.driverId)
    }

    @Test
    fun `getCurrent propagates 4xx as ClientRequestException`() = runTest {
        val client = client { respondError(HttpStatusCode.NotFound) }
        try {
            client.getCurrent()
            fail("expected exception on 404")
        } catch (e: ClientRequestException) {
            assertEquals(404, e.response.status.value)
        }
    }

    @Test
    fun `getCurrent propagates 5xx as ServerResponseException`() = runTest {
        val client = client { respondError(HttpStatusCode.InternalServerError) }
        try {
            client.getCurrent()
            fail("expected exception on 500")
        } catch (e: ServerResponseException) {
            assertEquals(500, e.response.status.value)
        }
    }

    @Test
    fun `getCurrent tolerates an empty envelope`() = runTest {
        val client = client { jsonOk("""{"season":0,"races":[]}""") }
        val dto = client.getCurrent()
        assertEquals(0, dto.races.size)
    }

    @Test
    fun `getCurrent with forceRefresh sends the no-cache header`() = runTest {
        var cacheControl: String? = null
        val client = client { req ->
            cacheControl = req.headers[HttpHeaders.CacheControl]
            jsonOk("""{"season":0,"races":[]}""")
        }
        client.getCurrent(forceRefresh = true)
        assertTrue(
            "expected no-cache header, was: $cacheControl",
            cacheControl?.contains("no-cache", ignoreCase = true) == true,
        )
    }

    @Test
    fun `getCurrent omits the no-cache header on a normal request`() = runTest {
        var cacheControl: String? = null
        val client = client { req ->
            cacheControl = req.headers[HttpHeaders.CacheControl]
            jsonOk("""{"season":0,"races":[]}""")
        }
        client.getCurrent()
        assertTrue(
            "expected no Cache-Control header on a normal request, was: $cacheControl",
            cacheControl == null,
        )
    }
}
