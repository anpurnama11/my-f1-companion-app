package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.data.F1API_BASE
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adversarial verification: [GetRoundPodiumUseCase] slices
 * `results.take(3)` from the underlying [GetRoundResultsUseCase]
 * response. When the source has zero results (empty `results`
 * array), the use case returns `Outcome.Failure("No results")` so
 * the Schedule screen's `PodiumCell` shows a retry row instead of
 * a silent blank cell.
 *
 * The use case is `core`-pure (no Android imports), so the test
 * uses the same MockEngine harness as the other use case tests.
 */
class GetRoundPodiumUseCaseEmptyResultsTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun podiumUseCase(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GetRoundPodiumUseCase = GetRoundPodiumUseCase(
        GetRoundResultsUseCase(
            HttpClient(MockEngine(handler)) {
                expectSuccess = true
                install(ContentNegotiation) { json(json) }
                defaultRequest { url(F1API_BASE) }
            }
        )
    )

    private fun MockRequestHandleScope.jsonOk(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private val EMPTY_RESULTS_BODY = """
        { "season": 2024,
          "races": {
            "round": "1", "date": "2024-03-02", "time": "15:00:00Z",
            "raceId": "bahrein2024",
            "raceName": "Gulf Air Bahrain Grand Prix 2024",
            "circuit": [{
              "circuitId": "bahrain", "circuitName": "Bahrain International Circuit",
              "country": "Bahrain", "city": "Sakhir",
              "circuitLength": "5412km", "corners": 15
            }],
            "results": []
          }
        }
    """.trimIndent()

    @Test
    fun `empty results array returns Failure so retry row shows`() = runTest {
        val out = podiumUseCase { jsonOk(EMPTY_RESULTS_BODY) }.invoke(year = 2024, round = 1)
        assertTrue("expected Failure (not Success), was $out", out is Outcome.Failure)
        assertEquals("No results", (out as Outcome.Failure).errorMessage)
    }
}
