package com.anpurnama.f1_app.core.cache

/**
 * Aggregate outcome of a bundle refresh — used by the WorkManager
 * `CacheSyncWorker` to report per-resource results from a single
 * periodic tick.
 *
 * A bundle is **best-effort**: per-resource failures are normal.
 * [requiresRetry] is the one worker policy: any retryable failure wins;
 * refreshed, fresh-skipped, deferred, and permanent outcomes are neutral.
 * Every cache resource uses the same five-outcome refresh contract.
 *
 * Generic over the resource key string to keep this type in
 * `core/cache/` without leaking F1-specific concepts.
 */
data class BundleRefreshResult(
    val entries: List<Entry>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val requiresRetry: Boolean
        get() = entries.any { it.result is RefreshResult.RetryableFailure }

    data class Entry(
        val key: String,
        val result: RefreshResult,
    )

    companion object {
        val Empty: BundleRefreshResult = BundleRefreshResult(emptyList())
    }
}
