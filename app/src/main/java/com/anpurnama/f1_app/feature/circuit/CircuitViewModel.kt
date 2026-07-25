package com.anpurnama.f1_app.feature.circuit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.f1.GetCircuitMostWinsUseCase
import com.anpurnama.f1_app.f1.GetCircuitUseCase
import com.anpurnama.f1_app.f1.model.CircuitDetail
import com.anpurnama.f1_app.f1.model.CircuitMostWins
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Circuit detail ViewModel — drives two independently-failing sections:
 *
 *  - **metadata**: f1api.dev `/circuits/{circuitId}` (length, corners,
 *    first-GP year, all-time lap record with attribution).
 *  - **mostWins**: jolpica `/circuits/{id}/results/1.json` aggregated to
 *    the all-time most-winning driver and team at this circuit.
 *
 * **Init-less:** first subscription triggers [warmUp]; the cold stream
 * is `stateIn(Lazily)` so the first load fires once and subsequent
 * subscribers read the hot `StateFlow` without re-firing. Re-fire is
 * via [refresh] (pull-to-refresh) only.
 *
 * **Section independence:** each call writes to its own
 * [MutableStateFlow]; a failure on one never blanks the other. The
 * design's two stats can degrade independently — e.g. f1api.dev down
 * still shows "all-time most wins" via jolpica, and vice versa.
 *
 * `circuitId` is a constructor param forwarded to both use cases on
 * every fire; it does not change for the ViewModel's lifetime.
 */
class CircuitViewModel(
    val circuitId: String,
    private val getCircuit: suspend (String, Boolean) -> Outcome<CircuitDetail>,
    private val getCircuitMostWins: suspend (String, Boolean) -> Outcome<CircuitMostWins>,
) : ViewModel() {

    sealed interface UiState {
        data class Sections(
            val metadata: SectionUiState<CircuitDetail>,
            val mostWins: SectionUiState<CircuitMostWins>,
        ) : UiState
    }

    private val metadataState = MutableStateFlow<SectionUiState<CircuitDetail>>(SectionUiState.Loading)
    private val mostWinsState = MutableStateFlow<SectionUiState<CircuitMostWins>>(SectionUiState.Loading)

    val uiState: StateFlow<UiState> = combine(metadataState, mostWinsState) { metadata, mostWins ->
        UiState.Sections(metadata = metadata, mostWins = mostWins)
    }
        .onStart { warmUp() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = UiState.Sections(
                metadata = SectionUiState.Loading,
                mostWins = SectionUiState.Loading,
            ),
        )

    /** First-load warm-up: fires both use cases in parallel. */
    private fun warmUp() {
        viewModelScope.launch { loadMetadata(forceRefresh = false) }
        viewModelScope.launch { loadMostWins(forceRefresh = false) }
    }

    /** Public pull-to-refresh: re-fires both with `forceRefresh = true` (NO_CACHE). */
    fun refresh() {
        viewModelScope.launch { loadMetadata(forceRefresh = true) }
        viewModelScope.launch { loadMostWins(forceRefresh = true) }
    }

    private suspend fun loadMetadata(forceRefresh: Boolean) {
        metadataState.value = SectionUiState.Loading
        metadataState.value = getCircuit(circuitId, forceRefresh).toSection()
    }

    private suspend fun loadMostWins(forceRefresh: Boolean) {
        mostWinsState.value = SectionUiState.Loading
        mostWinsState.value = getCircuitMostWins(circuitId, forceRefresh).toSection()
    }
}

/**
 * `viewModelFactory` builder. Mirrors the Driver/Team pattern — the
 * factory captures the use case instance refs; the VM takes
 * `suspend (String, Boolean) -> Outcome<…>` lambdas so it never sees
 * the `Wiring` types.
 */
fun circuitViewModelFactory(
    circuitId: String,
    getCircuit: GetCircuitUseCase,
    getCircuitMostWins: GetCircuitMostWinsUseCase,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        CircuitViewModel(
            circuitId = circuitId,
            getCircuit = getCircuit::invoke,
            getCircuitMostWins = getCircuitMostWins::invoke,
        )
    }
}
