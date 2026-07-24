package com.anpurnama.f1_app.feature.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.model.TeamDetail
import com.anpurnama.f1_app.ui.theme.Spacing
import com.anpurnama.f1_app.ui.theme.TeamColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    teamId: String,
    viewModel: TeamViewModel = rememberTeamViewModel(teamId),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = (state as? TeamViewModel.UiState.Sections) ?: return
    PullToRefreshBox(
        isRefreshing = sections.detail is SectionUiState.Loading,
        onRefresh = viewModel::refresh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.normal),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            OutcomeContent(state = sections.detail, onRetry = viewModel::refresh) { detail ->
                TeamContent(detail)
            }
        }
    }
}

@Composable
private fun TeamContent(detail: TeamDetail) {
    val accent = TeamColors.forId(detail.teamId).takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Text("Car image unavailable", color = MaterialTheme.colorScheme.onSurface)
    }
    Text(detail.wordmark, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    detail.country?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text("Standings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(modifier = Modifier.padding(Spacing.normal), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(detail.standing?.let { "P${it.position}" } ?: "—", fontWeight = FontWeight.Bold)
                Text(detail.standing?.let { "${it.points} pts" } ?: "No current standing")
                Text(detail.standing?.let { "${it.wins} wins" } ?: "")
            }
            detail.firstAppearance?.let { Text("First appearance: $it") }
            detail.constructorsChampionships?.let { Text("Constructor titles: $it") }
            detail.driversChampionships?.let { Text("Driver titles: $it") }
        }
    }
}

@Composable
private fun rememberTeamViewModel(teamId: String): TeamViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(factory = teamViewModelFactory(teamId, wiring.getTeamDetail))
}
