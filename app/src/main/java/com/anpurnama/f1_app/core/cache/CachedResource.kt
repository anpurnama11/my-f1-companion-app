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
    data object Success : RefreshResult
    data class Failure(val message: String) : RefreshResult
}
