package com.anpurnama.f1_app.feature.homepage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.GetSeasonUseCase
import com.anpurnama.f1_app.f1.model.Season
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Homepage §2: season aggregates (completed GPs / total km / total laps / progress).
 *
 * Init-less contract: no `init { load() }` block. The first subscription to
 * [uiState] triggers [load] via `Flow.onStart`. The state stream is shared
 * with `WhileSubscribed(5_000)` so a short config-change window doesn't
 * re-fire the network call.
 *
 * Section independence: §2 is the only section in this slice, so [load]
 * drives the single use case. §1/§3 land in slice 02 alongside the next two
 * use cases; the VM grows use-case refs, not new state flows.
 */
class HomepageViewModel(
    private val getSeason: suspend (forceRefresh: Boolean) -> Outcome<Season>,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val season: Season) : UiState
        data class Failure(val errorMessage: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    val uiState: StateFlow<UiState> = _uiState
        .onStart { load() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading,
        )

    /**
     * Re-fires the use case with `forceRefresh = true` so the request adds
     * `Cache-Control: no-cache` and bypasses the HttpCache. Wired to the
     * Homepage pull-to-refresh affordance.
     */
    fun refresh() {
        viewModelScope.launch { load(forceRefresh = true) }
    }

    private suspend fun load(forceRefresh: Boolean = false) {
        _uiState.value = UiState.Loading
        when (val out = getSeason(forceRefresh)) {
            is Outcome.Success -> _uiState.value = UiState.Success(out.data)
            is Outcome.Failure -> _uiState.value = UiState.Failure(out.errorMessage)
            is Outcome.Loading -> { /* already loading — no transition needed */ }
        }
    }
}

/**
 * `viewModelFactory` builder for the Homepage screen. The factory captures
 * [getSeason] from `Wiring`; the VM takes the function reference
 * (`getSeason::invoke`) per the use-case-as-function-ref seam.
 */
fun homepageViewModelFactory(getSeason: GetSeasonUseCase): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { HomepageViewModel(getSeason = getSeason::invoke) }
    }
