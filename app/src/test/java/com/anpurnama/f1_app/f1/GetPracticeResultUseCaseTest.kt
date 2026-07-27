package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.F1API_BASE
import com.anpurnama.f1_app.f1.model.SessionType
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetPracticeResultUseCaseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun useCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetPracticeResultUseCase = GetPracticeResultUseCase(
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
            // Absolute alpha + f1api URLs built by the extensions override this base.
            defaultRequest { url(F1API_BASE) }
        }
    )

    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private val ROUNDS_BODY = """
        { "data": [
            { "id": "round_test1", "number": 1, "name": "Bahrain Grand Prix" },
            { "id": "round_test2", "number": 2, "name": "Saudi Arabian Grand Prix" }
        ] }
    """.trimIndent()

    private val DRIVERS_BODY = """
        { "season": 2024, "drivers": [
            { "driverId": "max_verstappen", "name": "Max", "surname": "Verstappen",
              "number": 1, "shortName": "VER", "teamId": "red_bull" },
            { "driverId": "leclerc", "name": "Charles", "surname": "Leclerc",
              "number": 16, "shortName": "LEC", "teamId": "ferrari" }
        ] }
    """.trimIndent()

    private val FP1_BODY = """
        { "data": {
            "code": "FP1", "title": "Practice 1",
            "season": { "year": 2024 },
            "round": { "id": "round_test1", "number": 1, "name": "Bahrain Grand Prix" },
            "results": [
              { "driver": { "id": "driver_ver", "abbreviation": "VER",
                            "given_name": "Max", "family_name": "Verstappen" },
                "team": { "id": "team_rbr", "name": "Red Bull Racing" },
                "position": 1, "position_text": "1", "time": "1:30.000",
                "car_number": 1, "components": {} },
              { "driver": { "id": "driver_lec", "abbreviation": "LEC",
                            "given_name": "Charles", "family_name": "Leclerc" },
                "team": { "id": "team_fer", "name": "Ferrari" },
                "position": 2, "position_text": "2", "time": "1:30.500",
                "car_number": 16, "components": {} }
            ]
        } }
    """.trimIndent()

    private val EMPTY_FP1_BODY = """
        { "data": {
            "code": "FP1", "title": "Practice 1",
            "season": { "year": 2024 },
            "round": { "id": "round_test1", "number": 1, "name": "Bahrain Grand Prix" },
            "results": []
        } }
    """.trimIndent()

    private fun alphaHandler(
        roundsBody: String = ROUNDS_BODY,
        drivers: String? = DRIVERS_BODY,
        results: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
            { jsonOk(FP1_BODY) },
        onRequest: (HttpRequestData) -> Unit = {},
    ): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { req ->
        onRequest(req)
        when {
            req.url.fullPath.contains("/core/rounds/") -> jsonOk(roundsBody)
            req.url.fullPath.contains("/drivers") ->
                drivers?.let { jsonOk(it) } ?: respondError(HttpStatusCode.NotFound)
            req.url.fullPath.contains("/results/") -> results.invoke(this, req)
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `invoke returns Success SessionResult with translated practiceResults on 200`() = runTest {
        val out = useCase(alphaHandler()).invoke(year = 2024, round = 1, session = SessionType.FP1)
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val sr = (out as Outcome.Success).data
        assertEquals(SessionType.FP1, sr.session)
        assertEquals(2024, sr.year)
        assertEquals(1, sr.round)
        assertEquals("Bahrain Grand Prix", sr.raceName)
        // Alpha responses do not supply circuit data.
        assertEquals("", sr.circuit.id)
        assertEquals(2, sr.practiceResults.size)

        val p1 = sr.practiceResults[0]
        assertEquals(1, p1.position)
        assertEquals("1:30.000", p1.time)
        assertEquals("max_verstappen", p1.driverId)
        assertEquals("Max Verstappen", p1.driverName)
        assertEquals("VER", p1.driverShortName)
        assertEquals(1, p1.driverNumber)
        assertEquals("red_bull", p1.teamId)
        assertEquals("Red Bull Racing", p1.teamName)

        val p2 = sr.practiceResults[1]
        assertEquals(2, p2.position)
        assertEquals("leclerc", p2.driverId)
        assertEquals("LEC", p2.driverShortName)
        assertEquals("ferrari", p2.teamId)
    }

    @Test
    fun `invoke keeps alpha opaque ids when the driver catalog fetch fails`() = runTest {
        val out = useCase(alphaHandler(drivers = null))
            .invoke(year = 2024, round = 1, session = SessionType.FP1)
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val sr = (out as Outcome.Success).data
        assertEquals("driver_ver", sr.practiceResults[0].driverId)
        assertEquals("team_rbr", sr.practiceResults[0].teamId)
        assertEquals("driver_lec", sr.practiceResults[1].driverId)
        assertEquals("team_fer", sr.practiceResults[1].teamId)
    }

    @Test
    fun `invoke keeps the alpha opaque id for a car number missing from the catalog`() = runTest {
        val partialCatalog = """
            { "season": 2024, "drivers": [
                { "driverId": "max_verstappen", "name": "Max", "surname": "Verstappen",
                  "number": 1, "shortName": "VER", "teamId": "red_bull" } ] }
        """.trimIndent()
        val out = useCase(alphaHandler(drivers = partialCatalog))
            .invoke(year = 2024, round = 1, session = SessionType.FP1)
        assertTrue(out is Outcome.Success)
        val sr = (out as Outcome.Success).data
        assertEquals("max_verstappen", sr.practiceResults[0].driverId)
        assertEquals("red_bull", sr.practiceResults[0].teamId)
        assertEquals("driver_lec", sr.practiceResults[1].driverId)
        assertEquals("team_fer", sr.practiceResults[1].teamId)
    }

    @Test
    fun `invoke fetches the season-matched driver catalog`() = runTest {
        val requestedPaths = mutableListOf<String>()
        val out = useCase(alphaHandler(onRequest = { requestedPaths += it.url.fullPath }))
            .invoke(year = 2024, round = 1, session = SessionType.FP1)
        assertTrue(out is Outcome.Success)
        assertTrue(
            "season-matched catalog fetch missing: $requestedPaths",
            requestedPaths.any { it.contains("/api/2024/drivers") },
        )
    }

    @Test
    fun `invoke returns Failure Session is unavailable when round is not on the alpha calendar`() = runTest {
        val out = useCase(alphaHandler(roundsBody = """
            { "data": [ { "id": "round_test1", "number": 1, "name": "Bahrain Grand Prix" } ] }
        """.trimIndent())).invoke(year = 2024, round = 99, session = SessionType.FP1)
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals("Session is unavailable", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `invoke maps an invalid session filter to the not-scheduled failure`() = runTest {
        var resultsHits = 0
        val out = useCase(alphaHandler(results = {
            resultsHits++
            jsonOk(FP1_BODY)
        })).invoke(year = 2024, round = 1, session = SessionType.Race)
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals("Session is unavailable", (out as Outcome.Failure).errorMessage)
        assertEquals("alpha results must not be fetched for an invalid filter", 0, resultsHits)
    }

    @Test
    fun `invoke returns Success with empty practiceResults when alpha has no practice data`() = runTest {
        val out = useCase(alphaHandler(results = { jsonOk(EMPTY_FP1_BODY) }))
            .invoke(year = 2024, round = 1, session = SessionType.FP1)
        assertTrue("expected Success, was $out", out is Outcome.Success)
        val sr = (out as Outcome.Success).data
        assertEquals(SessionType.FP1, sr.session)
        assertTrue("expected empty practice list", sr.practiceResults.isEmpty())
    }

    @Test
    fun `invoke returns Failure on 4xx at the results endpoint`() = runTest {
        val out = useCase(alphaHandler(results = { respondError(HttpStatusCode.NotFound) }))
            .invoke(year = 2024, round = 1, session = SessionType.FP2)
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        assertEquals("Request failed (404)", (out as Outcome.Failure).errorMessage)
    }

    @Test
    fun `invoke hits the alpha FP1 results path`() = runTest {
        val requestedPaths = mutableListOf<String>()
        val out = useCase(alphaHandler(onRequest = { requestedPaths += it.url.fullPath }))
            .invoke(year = 2024, round = 1, session = SessionType.FP1)
        assertTrue(out is Outcome.Success)
        val resultsPath = requestedPaths.firstOrNull { it.contains("/results/") }
        assertNotNull("alpha results request missing: $requestedPaths", resultsPath)
        assertTrue(
            "expected .../results/round_test1/FP1/, was $resultsPath",
            resultsPath!!.contains("/results/round_test1/FP1"),
        )
    }

    @Test
    fun `invoke routes FP2 and FP3 to their own alpha filter paths`() = runTest {
        val requestedFilters = mutableListOf<String>()
        val handler = alphaHandler(onRequest = { req ->
            // capture the filter segment of the path: .../results/{roundId}/{filter}/
            if (req.url.fullPath.contains("/results/")) {
                requestedFilters += req.url.fullPath.substringAfter("/results/")
                    .substringAfter("/").substringBefore("/")
            }
        })
        useCase(handler).invoke(year = 2024, round = 1, session = SessionType.FP2)
        useCase(handler).invoke(year = 2024, round = 1, session = SessionType.FP3)
        assertEquals(listOf("FP2", "FP3"), requestedFilters)
    }

    @Test
    fun `invoke surfaces a malformed alpha response as a real error, not not-scheduled`() = runTest {
        val out = useCase(alphaHandler(results = {
            jsonOk("{ this is not valid json }")
        })).invoke(year = 2024, round = 1, session = SessionType.FP1)
        assertTrue("expected Failure, was $out", out is Outcome.Failure)
        val msg = (out as Outcome.Failure).errorMessage
        assertTrue("deserialization error must surface its real message, was: $msg",
            msg != "Session is unavailable" && msg != "Network error")
    }

    @Test
    fun `invoke with forceRefresh sends the no-cache header on results and catalog`() = runTest {
        val noCachePaths = mutableListOf<String>()
        val out = useCase(alphaHandler(onRequest = { req ->
            if (req.headers[HttpHeaders.CacheControl]?.contains("no-cache", ignoreCase = true) == true) {
                noCachePaths += req.url.fullPath
            }
        })).invoke(year = 2024, round = 1, session = SessionType.FP1, forceRefresh = true)
        assertTrue(out is Outcome.Success)
        assertTrue(
            "expected no-cache on the results fetch, were: $noCachePaths",
            noCachePaths.any { it.contains("/results/") },
        )
        assertTrue(
            "expected no-cache on the season-matched catalog fetch, were: $noCachePaths",
            noCachePaths.any { it.contains("/api/2024/drivers") },
        )
    }
}
