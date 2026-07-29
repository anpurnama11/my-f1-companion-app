package com.anpurnama.f1_app.core.cache

/**
 * Aggregate outcome of a bundle refresh — used by the WorkManager
 * `CacheSyncWorker` to report per-resource results from a single
 * periodic tick.
 *
 * A bundle is **best-effort**: per-resource failures are normal.
 * The worker only retries on a total infrastructure failure
 * (`succeeded == 0 && entries.isNotEmpty()`) so exponential backoff
 * does not amplify a partial outage.
 *
 * Generic over the resource key string to keep this type in
 * `core/cache/` without leaking F1-specific concepts.
 */
data class BundleRefreshResult(
    val entries: List<Entry>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val succeeded: Int get() = entries.count { it.result is RefreshResult.Success }
    val failed: Int get() = entries.count { it.result is RefreshResult.Failure }

    /**
     * True only when at least one resource was attempted and **none**
     * succeeded. The worker uses this to decide between
     * `Result.success()` (partial failure, keep going) and
     * `Result.retry()` (transient infrastructure failure, defer to
     * backoff).
     */
    fun isTotalFailure(): Boolean = entries.isNotEmpty() && succeeded == 0

    data class Entry(
        val key: String,
        val result: RefreshResult,
    )

    companion object {
        val Empty: BundleRefreshResult = BundleRefreshResult(emptyList())
    }
}
