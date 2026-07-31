package com.anpurnama.f1_app.feature.sessionresult

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
import com.anpurnama.f1_app.f1.GetFastestPitstopUseCase
import com.anpurnama.f1_app.f1.GetSessionResultUseCase
import com.anpurnama.f1_app.f1.model.FastestPitstop
import com.anpurnama.f1_app.f1.model.SessionResult
import com.anpurnama.f1_app.f1.model.SessionType
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

class SessionResultViewModel(
    val year: Int,
    val round: Int,
    val session: SessionType,
    private val getSessionResult: suspend (Int, Int, SessionType, Boolean) -> Outcome<SessionResult>,
    private val getFastestPitstop: (suspend (Int, Int, Boolean) -> Outcome<FastestPitstop?>)? = null,
    private val observeCachedResult: Flow<CachedResource<SessionResult>?>? = null,
    private val refreshCachedResult: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val observeCachedPitstop: Flow<CachedResource<FastestPitstop?>?>? = null,
    private val refreshCachedPitstop: (suspend (RefreshReason) -> RefreshResult)? = null,
    private val nowEpochMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    data class Sections(
        val result: SectionUiState<SessionResult>,
        val pitstop: SectionUiState<FastestPitstop?>,
    )

    private val resultState = MutableStateFlow<SectionUiState<SessionResult>>(SectionUiState.Loading)
    private val pitstopState = MutableStateFlow<SectionUiState<FastestPitstop?>>(
        if (session == SessionType.Race && (getFastestPitstop != null || observeCachedPitstop != null)) SectionUiState.Loading
        else SectionUiState.Content(null)
    )

    val uiState: StateFlow<Sections> = combine(resultState, pitstopState, ::Sections)
        .onStart { warmUp() }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            Sections(resultState.value, pitstopState.value),
        )

    private fun warmUp() {
        observeCachedResult
            ?.onEach { cached ->
                if (cached != null) {
                    val now = nowEpochMs()
                    resultState.value = cached.toSection(now)
                }
            }
            ?.launchIn(viewModelScope)
        observeCachedPitstop
            ?.onEach { cached ->
                if (cached != null) {
                    val now = nowEpochMs()
                    pitstopState.value = cached.toSection(now)
                }
            }
            ?.launchIn(viewModelScope)
        viewModelScope.launch { loadResult(false) }
        if (session == SessionType.Race && (getFastestPitstop != null || refreshCachedPitstop != null)) {
            viewModelScope.launch { loadPitstop(false) }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadResult(true) }
        if (session == SessionType.Race && (getFastestPitstop != null || refreshCachedPitstop != null)) {
            viewModelScope.launch { loadPitstop(true) }
        }
    }

    private suspend fun loadResult(forceRefresh: Boolean) {
        val refresh = refreshCachedResult
        if (observeCachedResult != null && refresh != null) {
            val result = resultState.refreshCachedSection(forceRefresh, refresh)
            // Fall through to direct network only when the cache explicitly
            // indicates it cannot serve this resource (not the active season).
            // A generic refresh failure (network error, server error) must keep
            // the error state and not trigger a second request.
            when (result) {
                is RefreshResult.PermanentFailure -> if (!result.isCurrentSeasonBoundary()) return
                RefreshResult.Deferred -> {
                    if (resultState.value !is SectionUiState.Content) {
                        resultState.value = SectionUiState.Error(SessionNotCompleteMessage)
                    }
                    return
                }
                RefreshResult.Refreshed,
                RefreshResult.SkippedFresh,
                is RefreshResult.RetryableFailure,
                -> return
            }
        }
        resultState.value = SectionUiState.Loading
        resultState.value = getSessionResult(year, round, session, forceRefresh).toSection()
    }

    private suspend fun loadPitstop(forceRefresh: Boolean) {
        val refresh = refreshCachedPitstop
        if (observeCachedPitstop != null && refresh != null) {
            val result = pitstopState.refreshCachedSection(forceRefresh, refresh)
            // Fall through to direct network only when the cache explicitly
            // indicates it cannot serve this resource (not the active season).
            // A generic refresh failure must keep the error state.
            when (result) {
                is RefreshResult.PermanentFailure -> if (!result.isCurrentSeasonBoundary()) return
                RefreshResult.Deferred -> {
                    if (pitstopState.value !is SectionUiState.Content) {
                        pitstopState.value = SectionUiState.Error(SessionNotCompleteMessage)
                    }
                    return
                }
                RefreshResult.Refreshed,
                RefreshResult.SkippedFresh,
                is RefreshResult.RetryableFailure,
                -> return
            }
        }
        pitstopState.value = SectionUiState.Loading
        pitstopState.value = getFastestPitstop!!(year, round, forceRefresh).toSection()
    }
}

private const val SessionNotCompleteMessage = "Session not yet complete"

private fun RefreshResult.isCurrentSeasonBoundary(): Boolean = when (this) {
    is RefreshResult.PermanentFailure -> message == "Not the active season" || message == "No active season"
    else -> false
}

fun sessionResultViewModelFactory(
    year: Int,
    round: Int,
    session: SessionType,
    getSessionResult: GetSessionResultUseCase,
    getFastestPitstop: GetFastestPitstopUseCase,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        SessionResultViewModel(
            year = year,
            round = round,
            session = session,
            getSessionResult = getSessionResult::invoke,
            getFastestPitstop = getFastestPitstop::invoke,
        )
    }
}

fun sessionResultViewModelFactory(
    year: Int,
    round: Int,
    session: SessionType,
    getSessionResult: GetSessionResultUseCase,
    getFastestPitstop: GetFastestPitstopUseCase,
    sessionResultsCacheRepository: com.anpurnama.f1_app.f1.cache.SessionResultsCacheRepository,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        SessionResultViewModel(
            year = year,
            round = round,
            session = session,
            getSessionResult = getSessionResult::invoke,
            getFastestPitstop = getFastestPitstop::invoke,
            observeCachedResult = sessionResultsCacheRepository.observeSessionResult(year, round, session),
            refreshCachedResult = { reason ->
                sessionResultsCacheRepository.refreshSessionResult(year, round, session, reason)
            },
            observeCachedPitstop = if (session == SessionType.Race) sessionResultsCacheRepository.observePitstops(year, round) else null,
            refreshCachedPitstop = if (session == SessionType.Race) { reason ->
                sessionResultsCacheRepository.refreshPitstops(year, round, reason)
            } else null,
        )
    }
}
