package com.anpurnama.f1_app.core.ui

import com.anpurnama.f1_app.core.Outcome

/**
 * VM → UI transport for a screen section. This is *not* a result type —
 * it describes what the screen should render, named for the screen's
 * vocabulary: [Loading] (spinner), [Error] (message + retry), [Content]
 * (the data to render). The generic operation result [Outcome] is mapped
 * to this at the VM seam so the composable never imports [Outcome].
 *
 * One per independently-failing section (see `HomepageViewModel.UiState.Sections`).
 * Rendered by the shared [OutcomeContent] family.
 */
sealed interface SectionUiState<out T> {
    data object Loading : SectionUiState<Nothing>
    data class Error(val message: String) : SectionUiState<Nothing>
    data class Content<T>(
        val data: T,
        val sync: ContentSyncStatus = ContentSyncStatus.Fresh,
    ) : SectionUiState<T>
}

sealed interface ContentSyncStatus {
    data object Fresh : ContentSyncStatus
    data object Stale : ContentSyncStatus
    data object Refreshing : ContentSyncStatus
    data class RefreshFailed(val message: String) : ContentSyncStatus
}

/** Collapse a data-layer [Outcome] into a UI [SectionUiState] at the VM seam. */
fun <T> Outcome<T>.toSection(): SectionUiState<T> = when (this) {
    is Outcome.Loading -> SectionUiState.Loading
    is Outcome.Failure -> SectionUiState.Error(errorMessage)
    is Outcome.Success -> SectionUiState.Content(data)
}
