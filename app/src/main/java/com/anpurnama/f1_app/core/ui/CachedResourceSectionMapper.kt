package com.anpurnama.f1_app.core.ui

import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshAttemptStatus

fun <T> CachedResource<T>.toSection(nowEpochMs: Long): SectionUiState.Content<T> {
    val sync = when (val status = snapshot.lastAttemptStatus) {
        is RefreshAttemptStatus.Failed -> ContentSyncStatus.RefreshFailed(status.message)
        RefreshAttemptStatus.Succeeded, null -> if (isStale(nowEpochMs)) ContentSyncStatus.Stale else ContentSyncStatus.Fresh
    }
    return SectionUiState.Content(data, sync)
}
