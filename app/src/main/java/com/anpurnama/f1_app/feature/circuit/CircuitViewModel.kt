package com.anpurnama.f1_app.feature.circuit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.cache.CachedResource
import com.anpurnama.f1_app.core.cache.RefreshReason
import com.anpurnama.f1_app.core.cache.RefreshResult
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.refreshCachedSection
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.f1.GetCircuitMostWinsUseCase
import com.anpurnama.f1_app.f1.GetCircuitUseCase
import com.anpurnama.f1_app.f1.model.CircuitDetail
import com.anpurnama.f1_app.f1.model.CircuitMostWins
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Circuit detail ViewModel — drives two independently-failing sections:
 *
 *  - **metadata**: f1api.dev `/circuits/{circuitId}` (length, corners,
 *    first-GP year, all-time lap record with attribution).
 *  - **mostWins**: jolpica `/circuits/{id}/results/1.json` aggregated to
 *    the all-time most-winning driver and team at this circuit.
 *
 * When cache observers are provided ([observeCachedMetadata],
 * [observeCachedMostWins]), the ViewModel subscribes to durable snapshots
 * and renders cached data as [SectionUiState.Content] with sync status.
 * Pull-to-refresh calls the corresponding [refreshCached*] function and
 * preserves cached content through stale/refreshing/failed states.
 * Without cache, it falls back to the direct network use cases (init-less
 * [Outcome] → [SectionUiState] mapping).
 *
 * **Section independence:** each section has its own [MutableStateFlow];
 * a failure on one never blanks the other.
 */
class CircuitViewModel(
    val circuitId: String,
    private val getCircuit: suspend (String, Boolean) -> Outcome<CircuitDetail>,
    private val getCircuitMostWins: suspend (String, Boolean) -> Outcome<CircuitMostWins>,
    private val observeCachedMetadata: Flow<CachedResource<CircuitDetail>?>? = null,
    private val refreshCachedMetadata: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val observeCachedMostWins: Flow<CachedResource<CircuitMostWins>?>? = null,
    private val refreshCachedMostWins: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val nowEpochMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
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

    private fun warmUp() {
        observeCachedMetadata
            ?.onEach { cached ->
                if (cached != null) {
                    metadataState.value = cached.toSection(nowEpochMs())
                }
            }
            ?.launchIn(viewModelScope)
        observeCachedMostWins
            ?.onEach { cached ->
                if (cached != null) {
                    mostWinsState.value = cached.toSection(nowEpochMs())
                }
            }
            ?.launchIn(viewModelScope)
        viewModelScope.launch { loadMetadata(false) }
        viewModelScope.launch { loadMostWins(false) }
    }

    fun refresh() {
        viewModelScope.launch { loadMetadata(true) }
        viewModelScope.launch { loadMostWins(true) }
    }

    private suspend fun loadMetadata(forceRefresh: Boolean) {
        val refresh = refreshCachedMetadata
        if (observeCachedMetadata != null && refresh != null) {
            val result = metadataState.refreshCachedSection(forceRefresh, refresh)
            // Fall through to direct network only when the cache explicitly
            // indicates it cannot serve this resource (not the active season).
            // A generic refresh failure must keep the error state.
            when (result) {
                is RefreshResult.Success -> return
                is RefreshResult.Failure -> if (result.message != "Not the active season" &&
                    result.message != "No active season"
                ) return
                RefreshResult.Refreshed,
                RefreshResult.SkippedFresh,
                RefreshResult.Deferred,
                is RefreshResult.RetryableFailure,
                is RefreshResult.PermanentFailure,
                -> return
            }
        }
        metadataState.value = SectionUiState.Loading
        metadataState.value = getCircuit(circuitId, forceRefresh).toSection()
    }

    private suspend fun loadMostWins(forceRefresh: Boolean) {
        val refresh = refreshCachedMostWins
        if (observeCachedMostWins != null && refresh != null) {
            val result = mostWinsState.refreshCachedSection(forceRefresh, refresh)
            // Fall through to direct network only when the cache explicitly
            // indicates it cannot serve this resource (not the active season).
            // A generic refresh failure must keep the error state.
            when (result) {
                is RefreshResult.Success -> return
                is RefreshResult.Failure -> if (result.message != "Not the active season" &&
                    result.message != "No active season"
                ) return
                RefreshResult.Refreshed,
                RefreshResult.SkippedFresh,
                RefreshResult.Deferred,
                is RefreshResult.RetryableFailure,
                is RefreshResult.PermanentFailure,
                -> return
            }
        }
        mostWinsState.value = SectionUiState.Loading
        mostWinsState.value = getCircuitMostWins(circuitId, forceRefresh).toSection()
    }
}

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

fun circuitViewModelFactory(
    circuitId: String,
    getCircuit: GetCircuitUseCase,
    getCircuitMostWins: GetCircuitMostWinsUseCase,
    nonSeasonResourcesCacheRepository: com.anpurnama.f1_app.f1.cache.NonSeasonResourcesCacheRepository,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        CircuitViewModel(
            circuitId = circuitId,
            getCircuit = getCircuit::invoke,
            getCircuitMostWins = getCircuitMostWins::invoke,
            observeCachedMetadata = nonSeasonResourcesCacheRepository.observeCircuitMetadata(circuitId),
            refreshCachedMetadata = { reason ->
                nonSeasonResourcesCacheRepository.refreshCircuitMetadata(circuitId, reason)
            },
            observeCachedMostWins = nonSeasonResourcesCacheRepository.observeCircuitMostWins(circuitId),
            refreshCachedMostWins = { reason ->
                nonSeasonResourcesCacheRepository.refreshCircuitMostWins(circuitId, reason)
            },
        )
    }
}
