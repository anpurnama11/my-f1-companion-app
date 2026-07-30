package com.anpurnama.f1_app.core.cache

/**
 * Aggregate outcome of a bundle refresh — used by the WorkManager
 * `CacheSyncWorker` to report per-resource results from a single
 * periodic tick.
 *
 * A bundle is **best-effort**: per-resource failures are normal.
 * During the #67 expand step, migrated current-season outcomes and
 * legacy session outcomes coexist. [requiresRetry] is the one worker
 * policy: any migrated retryable failure wins; legacy failures retain
 * their old retry-only-when-all-legacy-work-failed behavior until #68.
 *
 * Generic over the resource key string to keep this type in
 * `core/cache/` without leaking F1-specific concepts.
 */
data class BundleRefreshResult(
    val entries: List<Entry>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val requiresRetry: Boolean
        get() = entries.any { it.result is RefreshResult.RetryableFailure } ||
            legacyTotalFailure()

    private fun legacyTotalFailure(): Boolean {
        val hasLegacyFailure = entries.any { it.result is RefreshResult.Failure }
        val hasSuccessfulWrite = entries.any {
            it.result is RefreshResult.Refreshed || it.result is RefreshResult.Success
        }
        return hasLegacyFailure && !hasSuccessfulWrite
    }

    data class Entry(
        val key: String,
        val result: RefreshResult,
    )

    companion object {
        val Empty: BundleRefreshResult = BundleRefreshResult(emptyList())
    }
}
