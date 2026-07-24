package com.anpurnama.f1_app.feature.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.f1.GetTeamDetailUseCase
import com.anpurnama.f1_app.f1.model.TeamDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeamViewModel(
    val teamId: String,
    private val getTeamDetail: suspend (String, Boolean) -> Outcome<TeamDetail>,
) : ViewModel() {
    sealed interface UiState {
        data class Sections(val detail: SectionUiState<TeamDetail>) : UiState
    }

    private val detailState = MutableStateFlow<SectionUiState<TeamDetail>>(SectionUiState.Loading)
    val uiState: StateFlow<UiState> = detailState
        .map { UiState.Sections(it) }
        .onStart { load(false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = UiState.Sections(SectionUiState.Loading),
        )

    fun refresh() {
        viewModelScope.launch { load(true) }
    }

    private suspend fun load(forceRefresh: Boolean) {
        detailState.value = SectionUiState.Loading
        detailState.value = getTeamDetail(teamId, forceRefresh).toSection()
    }
}

fun teamViewModelFactory(
    teamId: String,
    getTeamDetail: GetTeamDetailUseCase,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        TeamViewModel(teamId, getTeamDetail::invoke)
    }
}
