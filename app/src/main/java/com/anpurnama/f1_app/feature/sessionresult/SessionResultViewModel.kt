package com.anpurnama.f1_app.feature.sessionresult

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.f1.GetFastestPitstopUseCase
import com.anpurnama.f1_app.f1.GetSessionResultUseCase
import com.anpurnama.f1_app.f1.model.FastestPitstop
import com.anpurnama.f1_app.f1.model.SessionResult
import com.anpurnama.f1_app.f1.model.SessionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionResultViewModel(
    val year: Int,
    val round: Int,
    val session: SessionType,
    private val getSessionResult: suspend (Int, Int, SessionType, Boolean) -> Outcome<SessionResult>,
    private val getFastestPitstop: (suspend (Int, Int, Boolean) -> Outcome<FastestPitstop?>)? = null,
) : ViewModel() {
    data class Sections(
        val result: SectionUiState<SessionResult>,
        val pitstop: SectionUiState<FastestPitstop?>,
    )

    private val resultState = MutableStateFlow<SectionUiState<SessionResult>>(SectionUiState.Loading)
    private val pitstopState = MutableStateFlow<SectionUiState<FastestPitstop?>>(
        if (session == SessionType.Race && getFastestPitstop != null) SectionUiState.Loading
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
        viewModelScope.launch { loadResult(false) }
        if (session == SessionType.Race && getFastestPitstop != null) {
            viewModelScope.launch { loadPitstop(false) }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadResult(true) }
        if (session == SessionType.Race && getFastestPitstop != null) {
            viewModelScope.launch { loadPitstop(true) }
        }
    }

    private suspend fun loadResult(forceRefresh: Boolean) {
        resultState.value = SectionUiState.Loading
        resultState.value = getSessionResult(year, round, session, forceRefresh).toSection()
    }

    private suspend fun loadPitstop(forceRefresh: Boolean) {
        pitstopState.value = SectionUiState.Loading
        pitstopState.value = getFastestPitstop!!(year, round, forceRefresh).toSection()
    }
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
