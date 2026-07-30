package com.anpurnama.f1_app.core.cache

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class RefreshFailureClassifierTest {

    @Test
    fun `HTTP retryability follows the refresh contract`() = runTest {
        val cases = listOf(
            HttpStatusCode.RequestTimeout to true,
            HttpStatusCode.TooManyRequests to true,
            HttpStatusCode.InternalServerError to true,
            HttpStatusCode.BadRequest to false,
            HttpStatusCode.NotFound to false,
        )

        cases.forEach { (status, retryable) ->
            val exception = runCatching {
                HttpClient(MockEngine { respondError(status) }) {
                    expectSuccess = true
                }.use { it.get("https://example.test") }
            }.exceptionOrNull()!!

            val result = RefreshFailureClassifier.classify(exception)

            assertEquals(
                "HTTP $status",
                retryable,
                result is RefreshResult.RetryableFailure,
            )
            assertEquals(
                "HTTP $status",
                !retryable,
                result is RefreshResult.PermanentFailure,
            )
        }
    }

    @Test
    fun `timeouts connectivity and serializer-detected malformed payloads are retryable`() {
        val failures = listOf(
            HttpRequestTimeoutException(HttpRequestBuilder()),
            IOException("connection reset"),
            SerializationException("malformed JSON"),
        )

        failures.forEach { failure ->
            val result = RefreshFailureClassifier.classify(failure)
            assertEquals(failure::class.simpleName, true, result is RefreshResult.RetryableFailure)
        }
    }

    @Test(expected = CancellationException::class)
    fun `cancellation propagates`() {
        RefreshFailureClassifier.classify(CancellationException("cancelled"))
    }

    @Test(expected = IllegalStateException::class)
    fun `unknown programmer failures propagate`() {
        RefreshFailureClassifier.classify(IllegalStateException("bug"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unrelated argument failures propagate`() {
        RefreshFailureClassifier.classify(IllegalArgumentException("invalid invariant"))
    }
}
