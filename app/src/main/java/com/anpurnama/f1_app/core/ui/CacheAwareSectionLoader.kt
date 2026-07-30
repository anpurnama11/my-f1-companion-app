package com.anpurnama.f1_app.core.ui

import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.cache.failureMessageOrNull
import kotlinx.coroutines.flow.MutableStateFlow

suspend fun <T> MutableStateFlow<SectionUiState<T>>.refreshCachedSection(
    forceRefresh: Boolean,
    refresh: suspend (RefreshReason) -> RefreshResult,
): RefreshResult {
    val current = value
    value = if (current is SectionUiState.Content) {
        current.copy(sync = ContentSyncStatus.Refreshing)
    } else {
        SectionUiState.Loading
    }
    return when (val result = refresh(if (forceRefresh) RefreshReason.PullToRefresh else RefreshReason.StaleOpen)) {
        RefreshResult.Refreshed,
        RefreshResult.SkippedFresh,
        RefreshResult.Success,
        -> {
            if (value is SectionUiState.Content) {
                @Suppress("UNCHECKED_CAST")
                value = (value as SectionUiState.Content<T>).copy(sync = ContentSyncStatus.Fresh)
            }
            result
        }
        RefreshResult.Deferred -> {
            value = current
            result
        }
        is RefreshResult.RetryableFailure,
        is RefreshResult.PermanentFailure,
        is RefreshResult.Failure,
        -> {
            val after = value
            value = if (after is SectionUiState.Content) {
                after.copy(sync = ContentSyncStatus.RefreshFailed(result.failureMessageOrNull!!))
            } else {
                SectionUiState.Error(result.failureMessageOrNull!!)
            }
            result
        }
    }
}
