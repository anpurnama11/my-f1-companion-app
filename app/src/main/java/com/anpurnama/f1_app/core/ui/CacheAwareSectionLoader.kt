package com.anpurnama.f1_app.core.ui

import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import kotlinx.coroutines.flow.MutableStateFlow

suspend fun <T> MutableStateFlow<SectionUiState<T>>.refreshCachedSection(
    forceRefresh: Boolean,
    refresh: suspend (RefreshReason) -> RefreshResult,
) {
    val current = value
    value = if (current is SectionUiState.Content) {
        current.copy(sync = ContentSyncStatus.Refreshing)
    } else {
        SectionUiState.Loading
    }
    when (val result = refresh(if (forceRefresh) RefreshReason.PullToRefresh else RefreshReason.StaleOpen)) {
        RefreshResult.Success -> if (value is SectionUiState.Content) {
            @Suppress("UNCHECKED_CAST")
            value = (value as SectionUiState.Content<T>).copy(sync = ContentSyncStatus.Fresh)
        }
        is RefreshResult.Failure -> {
            val after = value
            value = if (after is SectionUiState.Content) {
                after.copy(sync = ContentSyncStatus.RefreshFailed(result.message))
            } else {
                SectionUiState.Error(result.message)
            }
        }
    }
}
