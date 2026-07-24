package com.anpurnama.f1_app.feature.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.core.ui.toSection
import com.anpurnama.f1_app.f1.GetDriverDetailUseCase
import com.anpurnama.f1_app.f1.model.DriverDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DriverViewModel(
    val driverId: String,
    private val getDriverDetail: suspend (String, Boolean) -> Outcome<DriverDetail>,
) : ViewModel() {
    sealed interface UiState {
        data class Sections(val detail: SectionUiState<DriverDetail>) : UiState
    }

    private val detailState = MutableStateFlow<SectionUiState<DriverDetail>>(SectionUiState.Loading)
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
        detailState.value = getDriverDetail(driverId, forceRefresh).toSection()
    }
}

fun driverViewModelFactory(
    driverId: String,
    getDriverDetail: GetDriverDetailUseCase,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        DriverViewModel(driverId, getDriverDetail::invoke)
    }
}
