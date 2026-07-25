package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.F1API_BASE
import com.anpurnama.f1_app.f1.data.getCircuit
import com.anpurnama.f1_app.f1.model.CircuitDetail
import com.anpurnama.f1_app.f1.model.LapRecord
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

class GetCircuitUseCaseTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = GetCircuitUseCase(
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
    fun `mapper converts circuitLength int meters to decimal km`() = runTest {
        val out = useCase {
            jsonOk(
                """
                {"total":1,"circuit":[{
                  "circuitId":"bahrain","circuitName":"Bahrain International Circuit",
                  "country":"Bahrain","city":"Sakhir",
                  "circuitLength":5412,"numberOfCorners":15,
                  "firstParticipationYear":2004,
                  "lapRecord":"1:31:447",
                  "fastestLapDriverId":"delarosa","fastestLapTeamId":"mclaren","fastestLapYear":2005
                }]}
                """.trimIndent(),
            )
        }.invoke("bahrain")

        assertTrue(out is Outcome.Success)
        val c = (out as Outcome.Success).data
        assertEquals(5.412, c.circuitLengthKm, 0.0)
        assertEquals(15, c.numberOfCorners)
        assertEquals(2004, c.firstParticipationYear)
        assertEquals(LapRecord("1:31:447", "delarosa", "mclaren", 2005), c.lapRecord)
    }

    @Test
    fun `mapper collapses partial lap record fields to null`() = runTest {
        val out = useCase {
            jsonOk(
                """
                {"total":1,"circuit":[{
                  "circuitId":"bahrain","circuitName":"Bahrain",
                  "circuitLength":5412,
                  "lapRecord":"1:31:447",
                  "fastestLapDriverId":"delarosa"
                }]}
                """.trimIndent(),
            )
        }.invoke("bahrain")
        val c = (out as Outcome.Success).data
        assertNull(c.lapRecord)
    }

    @Test
    fun `mapper returns null model on an empty circuit array`() = runTest {
        val out = useCase {
            jsonOk("""{"total":0,"circuit":[]}""")
        }.invoke("any_unknown")
        assertTrue(out is Outcome.Failure)
        assertEquals("Circuit any_unknown not found", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `404 becomes a data failure with the standard error message shape`() = runTest {
        val out = useCase { respondError(HttpStatusCode.NotFound) }
            .invoke("missing")
        assertTrue(out is Outcome.Failure)
        assertEquals("Request failed (404)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `500 becomes a server error failure`() = runTest {
        val out = useCase { respondError(HttpStatusCode.InternalServerError) }
            .invoke("bahrain")
        assertTrue(out is Outcome.Failure)
        assertEquals("Server error (500)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `mapper on a circuit with no lap record keeps the model valid`() = runTest {
        val out = useCase {
            jsonOk(
                """
                {"total":1,"circuit":[{
                  "circuitId":"madring","circuitName":"Madring",
                  "country":"Spain","city":"Madrid",
                  "circuitLength":5500,
                  "numberOfCorners":16,
                  "firstParticipationYear":2026
                }]}
                """.trimIndent(),
            )
        }.invoke("madring")
        val c = (out as Outcome.Success).data
        assertEquals(5.5, c.circuitLengthKm, 0.0)
        assertNull(c.lapRecord)
        assertEquals("madring", c.id)
        assertEquals("Madring", c.name)
        assertEquals("Spain", c.country)
        assertEquals("Madrid", c.city)
    }
}
