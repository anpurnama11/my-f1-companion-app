package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.CircuitWinnersResponseDto
import com.anpurnama.f1_app.f1.data.JOLPICA_BASE
import com.anpurnama.f1_app.f1.data.getCircuitWinners
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

class GetCircuitMostWinsUseCaseTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = GetCircuitMostWinsUseCase(
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

    /**
     * Build a P1 row JSON for a single race. The jolpica envelope shape is
     * `{ MRData: { RaceTable: { Races: [ { Results: [<P1>] } ] } } }` —
     * one race = one P1 row, so each fixture entry below is a separate
     * `Races[]` element.
     */
    private fun p1Race(
        driverId: String, given: String, family: String,
        constructorId: String, constructorName: String,
        season: Int = 2024, round: Int = 1,
    ): String = """
        {"season":"$season","round":"$round",
         "Results":[{"position":"1",
           "Driver":{"driverId":"$driverId","givenName":"$given","familyName":"$family"},
           "Constructor":{"constructorId":"$constructorId","name":"$constructorName"}}]}
    """.trimIndent()

    private fun envelope(vararg raceResults: String) = """
        {"MRData":{"total":"${raceResults.size}","RaceTable":{"Races":[
          ${raceResults.joinToString(",\n")}
        ]}}}
    """.trimIndent()

    @Test
    fun `mapper picks the most winning driver and team from P1 rows`() {
        val dto = decode(envelope(
            p1Race("hamilton", "Lewis", "Hamilton", "mercedes", "Mercedes", 2024, 1),
            p1Race("hamilton", "Lewis", "Hamilton", "mercedes", "Mercedes", 2023, 1),
            p1Race("hamilton", "Lewis", "Hamilton", "mercedes", "Mercedes", 2022, 1),
            p1Race("hamilton", "Lewis", "Hamilton", "mercedes", "Mercedes", 2021, 1),
            p1Race("hamilton", "Lewis", "Hamilton", "mercedes", "Mercedes", 2020, 1),
            p1Race("vettel", "Sebastian", "Vettel", "ferrari", "Ferrari", 2019, 1),
            p1Race("vettel", "Sebastian", "Vettel", "ferrari", "Ferrari", 2018, 1),
            p1Race("vettel", "Sebastian", "Vettel", "redbull", "Red Bull", 2017, 1),
            p1Race("bottas", "Valtteri", "Bottas", "mercedes", "Mercedes", 2016, 1),
        ))

        val out = dto.toCircuitMostWins()

        assertEquals(9, out.totalRaces)
        assertNotNull(out.topDriver)
        assertEquals("hamilton", out.topDriver?.driverId)
        assertEquals("Lewis Hamilton", out.topDriver?.name)
        assertEquals(5, out.topDriver?.wins)
        assertNotNull(out.topTeam)
        // Mercedes has Hamilton 5 + Bottas 1 = 6 wins, Ferrari 2, Red Bull 1
        assertEquals("mercedes", out.topTeam?.teamId)
        assertEquals("Mercedes", out.topTeam?.name)
        assertEquals(6, out.topTeam?.wins)
    }

    @Test
    fun `mapper aggregates correctly for a tied all-time leader`() {
        val dto = decode(envelope(
            p1Race("schumacher", "Michael", "Schumacher", "ferrari", "Ferrari", 2010, 1),
            p1Race("schumacher", "Michael", "Schumacher", "ferrari", "Ferrari", 2009, 1),
            p1Race("schumacher", "Michael", "Schumacher", "ferrari", "Ferrari", 2008, 1),
            p1Race("schumacher", "Michael", "Schumacher", "ferrari", "Ferrari", 2007, 1),
            p1Race("schumacher", "Michael", "Schumacher", "ferrari", "Ferrari", 2006, 1),
            p1Race("hamilton", "Lewis", "Hamilton", "ferrari", "Ferrari", 2005, 1),
            p1Race("hamilton", "Lewis", "Hamilton", "ferrari", "Ferrari", 2004, 1),
            p1Race("hamilton", "Lewis", "Hamilton", "ferrari", "Ferrari", 2003, 1),
            p1Race("hamilton", "Lewis", "Hamilton", "ferrari", "Ferrari", 2002, 1),
            p1Race("hamilton", "Lewis", "Hamilton", "ferrari", "Ferrari", 2001, 1),
        ))

        val out = dto.toCircuitMostWins()
        assertEquals(10, out.totalRaces)
        assertEquals(5, out.topDriver?.wins)
        assertEquals(10, out.topTeam?.wins)
        // First winner in iteration order is "schumacher"
        assertEquals("schumacher", out.topDriver?.driverId)
    }

    @Test
    fun `mapper returns null leaders on an empty race list`() {
        val out = CircuitWinnersResponseDto().toCircuitMostWins()
        assertNull(out.topDriver)
        assertNull(out.topTeam)
        assertEquals(0, out.totalRaces)
    }

    @Test
    fun `mapper handles a P1 row missing driverId but with a valid constructorId`() {
        // A pre-1950 race with no driverId but a real constructor.
        val payload = """
            {"MRData":{"total":"2","RaceTable":{"Races":[
              {"season":"2024","round":"1","Results":[{"position":"1",
                "Driver":{"driverId":"hamilton","givenName":"Lewis","familyName":"Hamilton"},
                "Constructor":{"constructorId":"mercedes","name":"Mercedes"}}]},
              {"season":"1952","round":"3","Results":[{"position":"1",
                "Driver":{},
                "Constructor":{"constructorId":"ferrari","name":"Ferrari"}}]}
            ]}}}
        """.trimIndent()
        val dto = decode(payload)
        val out = dto.toCircuitMostWins()
        assertEquals("hamilton", out.topDriver?.driverId)
        // Mercedes 1, Ferrari 1 — tie; first wins
        assertEquals(1, out.topTeam?.wins)
        assertEquals("mercedes", out.topTeam?.teamId)
    }

    @Test
    fun `mapper returns a top team even when no driver rows have ids`() {
        val payload = """
            {"MRData":{"total":"2","RaceTable":{"Races":[
              {"season":"1950","round":"1","Results":[{"position":"1",
                "Driver":{},
                "Constructor":{"constructorId":"ferrari","name":"Ferrari"}}]},
              {"season":"1951","round":"1","Results":[{"position":"1",
                "Driver":{},
                "Constructor":{"constructorId":"ferrari","name":"Ferrari"}}]}
            ]}}}
        """.trimIndent()
        val dto = decode(payload)
        val out = dto.toCircuitMostWins()
        assertNull(out.topDriver)
        assertNotNull(out.topTeam)
        assertEquals("ferrari", out.topTeam?.teamId)
        assertEquals(2, out.topTeam?.wins)
    }

    @Test
    fun `mapper falls back to family name when jolpica omits given name`() {
        val payload = """
            {"MRData":{"total":"2","RaceTable":{"Races":[
              {"season":"2024","round":"1","Results":[{"position":"1",
                "Driver":{"driverId":"hamilton","familyName":"Hamilton"},
                "Constructor":{"constructorId":"mercedes","name":"Mercedes"}}]},
              {"season":"2023","round":"1","Results":[{"position":"1",
                "Driver":{"driverId":"hamilton","givenName":"Lewis"},
                "Constructor":{"constructorId":"mercedes","name":"Mercedes"}}]}
            ]}}}
        """.trimIndent()
        val dto = decode(payload)
        val out = dto.toCircuitMostWins()
        assertEquals("hamilton", out.topDriver?.driverId)
        // First row in iteration order contributes the leader's name.
        assertEquals("Hamilton", out.topDriver?.name)
    }

    @Test
    fun `mapper ignores P2 or worse rows and only counts position 1`() {
        // A single race where a P1 hamilton and a P2 hamilton are both in
        // the same Results array (this is the only place a non-P1 row
        // could appear under the same race).
        val payload = """
            {"MRData":{"total":"1","RaceTable":{"Races":[
              {"season":"2024","round":"1","Results":[
                {"position":"1","Driver":{"driverId":"hamilton","givenName":"Lewis","familyName":"Hamilton"},
                 "Constructor":{"constructorId":"mercedes","name":"Mercedes"}},
                {"position":"2","Driver":{"driverId":"hamilton","givenName":"Lewis","familyName":"Hamilton"},
                 "Constructor":{"constructorId":"ferrari","name":"Ferrari"}}
              ]}
            ]}}}
        """.trimIndent()
        val dto = decode(payload)
        val out = dto.toCircuitMostWins()
        assertEquals(1, out.totalRaces)
        assertEquals("hamilton", out.topDriver?.driverId)
        assertEquals(1, out.topDriver?.wins)
        // Mercedes 1, Ferrari 0 — top team is still mercedes
        assertEquals("mercedes", out.topTeam?.teamId)
    }

    @Test
    fun `use case surfaces a 404 as data failure`() = runTest {
        val out = useCase { respondError(HttpStatusCode.NotFound) }
            .invoke("missing")
        assertTrue(out is Outcome.Failure)
        assertEquals("Request failed (404)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `use case surfaces a 500 as server error failure`() = runTest {
        val out = useCase { respondError(HttpStatusCode.InternalServerError) }
            .invoke("bahrain")
        assertTrue(out is Outcome.Failure)
        assertEquals("Server error (500)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `use case returns success on a 200 with an empty race list`() = runTest {
        val out = useCase { jsonOk("""{"MRData":{"total":"0","RaceTable":{"Races":[]}}}""") }
            .invoke("bahrain")
        assertTrue(out is Outcome.Success)
        val model = (out as Outcome.Success).data
        assertNull(model.topDriver)
        assertNull(model.topTeam)
        assertEquals(0, model.totalRaces)
    }

    private fun decode(payload: String): CircuitWinnersResponseDto =
        json.decodeFromString(CircuitWinnersResponseDto.serializer(), payload)
}
