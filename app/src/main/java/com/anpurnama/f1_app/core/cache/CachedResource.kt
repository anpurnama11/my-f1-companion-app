package com.anpurnama.f1_app.core.cache

data class CachedResource<T>(
    val data: T,
    val snapshot: ResourceSnapshot,
) {
    fun isStale(nowEpochMs: Long): Boolean = snapshot.staleAfterEpochMs <= nowEpochMs
}

sealed interface RefreshReason {
    data object StaleOpen : RefreshReason
    data object PullToRefresh : RefreshReason
    data object Periodic : RefreshReason
}

sealed interface RefreshResult {
    data object Refreshed : RefreshResult
    data object SkippedFresh : RefreshResult
    data object Deferred : RefreshResult
    data class RetryableFailure(val message: String) : RefreshResult
    data class PermanentFailure(val message: String) : RefreshResult

    // Transitional outcomes owned by #68 (session resources) and #69
    // (non-season resources and final removal).
    data object Success : RefreshResult
    data class Failure(val message: String) : RefreshResult
}

val RefreshResult.failureMessageOrNull: String?
    get() = when (this) {
        is RefreshResult.RetryableFailure -> message
        is RefreshResult.PermanentFailure -> message
        is RefreshResult.Failure -> message
        else -> null
    }
