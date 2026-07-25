package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.f1.data.F1API_BASE
import com.anpurnama.f1_app.f1.data.JOLPICA_BASE
import com.anpurnama.f1_app.f1.data.getCircuit
import com.anpurnama.f1_app.f1.data.getCircuitWinners
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

class CircuitDetailApiTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        defaultRequest { url(F1API_BASE) }
    }

    private fun jolpicaClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        defaultRequest { url(JOLPICA_BASE) }
    }

    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    @Test
    fun `getCircuit decodes the live-shaped envelope and hits the right path`() = runTest {
        var requestedPath: String? = null
        val c = client { req ->
            requestedPath = req.url.fullPath
            jsonOk(
                """
                {
                  "api": "https://f1api.dev",
                  "url": "https://f1api.dev/api/circuits/bahrain",
                  "total": 1,
                  "circuit": [{
                    "circuitId": "bahrain",
                    "circuitName": "Bahrain International Circuit",
                    "country": "Bahrain",
                    "city": "Sakhir",
                    "circuitLength": 5412,
                    "lapRecord": "1:31:447",
                    "firstParticipationYear": 2004,
                    "numberOfCorners": 15,
                    "fastestLapDriverId": "delarosa",
                    "fastestLapTeamId": "mclaren",
                    "fastestLapYear": 2005,
                    "url": "http://en.wikipedia.org/wiki/BahrainInternationalCircuit"
                  }]
                }
                """.trimIndent(),
            )
        }
        val dto = c.getCircuit("bahrain")
        assertEquals("/api/circuits/bahrain", requestedPath)
        assertEquals(1, dto.circuit.size)
        assertEquals("bahrain", dto.circuit.single().circuitId)
        assertEquals(5412, dto.circuit.single().circuitLength)
        assertEquals("1:31:447", dto.circuit.single().lapRecord)
        assertEquals(2004, dto.circuit.single().firstParticipationYear)
        assertEquals(15, dto.circuit.single().numberOfCorners)
        assertEquals("delarosa", dto.circuit.single().fastestLapDriverId)
    }

    @Test
    fun `getCircuit tolerates missing optional fields and returns an empty list on 200 with no body`() = runTest {
        val c = client { jsonOk("""{"total":0,"circuit":[]}""") }
        val dto = c.getCircuit("unknown_circuit")
        assertEquals(0, dto.circuit.size)
    }

    @Test
    fun `getCircuit propagates 4xx as ClientRequestException`() = runTest {
        val c = client { respondError(HttpStatusCode.NotFound) }
        try {
            c.getCircuit("unknown_circuit")
            fail("expected exception on 404")
        } catch (e: ClientRequestException) {
            assertEquals(404, e.response.status.value)
        }
    }

    @Test
    fun `getCircuit propagates 5xx as ServerResponseException`() = runTest {
        val c = client { respondError(HttpStatusCode.InternalServerError) }
        try {
            c.getCircuit("bahrain")
            fail("expected exception on 500")
        } catch (e: ServerResponseException) {
            assertEquals(500, e.response.status.value)
        }
    }

    @Test
    fun `getCircuit sends no-cache header when forceRefresh is true`() = runTest {
        var cacheControl: String? = null
        val c = client { req ->
            cacheControl = req.headers[HttpHeaders.CacheControl]
            jsonOk("""{"total":0,"circuit":[]}""")
        }
        c.getCircuit("bahrain", forceRefresh = true)
        assertTrue(
            "expected no-cache header, was: $cacheControl",
            cacheControl?.contains("no-cache", ignoreCase = true) == true,
        )
    }

    @Test
    fun `getCircuitWinners decodes the jolpica envelope`() = runTest {
        val c = jolpicaClient { jsonOk(
            """
            {"MRData":{"total":"22","RaceTable":{"Races":[
              {"season":"2024","round":"1","Results":[
                {"position":"1","Driver":{"driverId":"maxverstappen","givenName":"Max","familyName":"Verstappen"},
                 "Constructor":{"constructorId":"redbull","name":"Red Bull"}}
              ]}
            ]}}}
            """.trimIndent()
        ) }
        val dto = c.getCircuitWinners("bahrain")
        assertEquals(1, dto.mrData.raceTable.races.size)
        val row = dto.mrData.raceTable.races.single().results.single()
        assertEquals("maxverstappen", row.driver.driverId)
        assertEquals("redbull", row.constructor.constructorId)
    }

    @Test
    fun `getCircuitWinners translates the 5 mapped ids and passes the rest through`() = runTest {
        val translations = mapOf(
            "austin" to "americas",
            "gilles_villeneuve" to "villeneuve",
            "hermanos_rodriguez" to "rodriguez",
            "lusail" to "losail",
            "montmelo" to "catalunya",
        )
        val passthrough = listOf("bahrain", "monza", "silverstone", "spa", "monaco")
        val paths = mutableListOf<String>()
        val c = jolpicaClient { req ->
            paths += req.url.fullPath
            jsonOk("""{"MRData":{"total":"0","RaceTable":{"Races":[]}}}""")
        }

        translations.forEach { (f1api, jolpica) ->
            c.getCircuitWinners(f1api)
        }
        passthrough.forEach { id ->
            c.getCircuitWinners(id)
        }

        translations.forEach { (f1api, jolpica) ->
            assertTrue(
                "expected $jolpica in path for $f1api, got: $paths",
                paths.any { it == "/ergast/f1/circuits/$jolpica/results/1.json" },
            )
        }
        passthrough.forEach { id ->
            assertTrue(
                "expected passthrough $id, got: $paths",
                paths.any { it == "/ergast/f1/circuits/$id/results/1.json" },
            )
        }
        assertEquals(10, paths.size)
    }

    @Test
    fun `getCircuitWinners propagates 4xx as ClientRequestException`() = runTest {
        val c = jolpicaClient { respondError(HttpStatusCode.NotFound) }
        try {
            c.getCircuitWinners("unknown_circuit")
            fail("expected exception on 404")
        } catch (e: ClientRequestException) {
            assertEquals(404, e.response.status.value)
        }
    }

    @Test
    fun `getCircuitWinners sends no-cache header when forceRefresh is true`() = runTest {
        var cacheControl: String? = null
        val c = jolpicaClient { req ->
            cacheControl = req.headers[HttpHeaders.CacheControl]
            jsonOk("""{"MRData":{"total":"0","RaceTable":{"Races":[]}}}""")
        }
        c.getCircuitWinners("bahrain", forceRefresh = true)
        assertTrue(
            "expected no-cache header, was: $cacheControl",
            cacheControl?.contains("no-cache", ignoreCase = true) == true,
        )
    }
}
