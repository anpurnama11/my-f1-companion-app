package com.anpurnama.f1_app.core.cache

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

object RefreshFailureClassifier {
    fun classify(throwable: Throwable): RefreshResult = when (throwable) {
        is CancellationException -> throw throwable
        is HttpRequestTimeoutException ->
            RefreshResult.RetryableFailure(throwable.message ?: "Request timed out")
        is ClientRequestException -> classifyClientError(throwable)
        is ServerResponseException ->
            RefreshResult.RetryableFailure("Server error (${throwable.response.status.value})")
        is IOException ->
            RefreshResult.RetryableFailure(throwable.message ?: "Network error")
        is SerializationException ->
            RefreshResult.RetryableFailure(throwable.message ?: "Invalid response")
        else -> throw throwable
    }

    private fun classifyClientError(throwable: ClientRequestException): RefreshResult {
        val status = throwable.response.status.value
        val message = "Request failed ($status)"
        return when (status) {
            HttpStatusCode.RequestTimeout.value,
            HttpStatusCode.TooManyRequests.value,
            -> RefreshResult.RetryableFailure(message)
            else -> RefreshResult.PermanentFailure(message)
        }
    }
}
