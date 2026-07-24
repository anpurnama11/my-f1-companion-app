package com.anpurnama.f1_app.feature.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.anpurnama.f1_app.f1.model.DriverDetail
import com.anpurnama.f1_app.ui.theme.Spacing
import com.anpurnama.f1_app.ui.theme.TeamColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverScreen(
    driverId: String,
    onTeamClick: (String) -> Unit = {},
    viewModel: DriverViewModel = rememberDriverViewModel(driverId),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections = (state as? DriverViewModel.UiState.Sections) ?: return
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
                DriverContent(detail, onTeamClick)
            }
        }
    }
}

@Composable
private fun DriverContent(detail: DriverDetail, onTeamClick: (String) -> Unit) {
    val accent = TeamColors.forId(detail.teamId).takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .height(180.dp)
                    .weight(0.8f)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("Headshot unavailable", color = MaterialTheme.colorScheme.onSurface)
            }
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .padding(Spacing.normal),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(detail.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                detail.shortName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                detail.number?.let { Text("#$it", style = MaterialTheme.typography.titleMedium) }
                Text(
                    text = detail.teamName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTeamClick(detail.teamId) }
                        .padding(top = Spacing.sm),
                )
            }
        }
    }
    Text("Standings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    StandingSnapshot(detail)
}

@Composable
private fun StandingSnapshot(detail: DriverDetail) {
    val standing = detail.standing
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.normal),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(standing?.let { "P${it.position}" } ?: "—", fontWeight = FontWeight.Bold)
            Text(standing?.let { "${it.points} pts" } ?: "No current standing")
            Text(standing?.let { "${it.wins} wins" } ?: "")
        }
    }
}

@Composable
private fun rememberDriverViewModel(driverId: String): DriverViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(factory = driverViewModelFactory(driverId, wiring.getDriverDetail))
}
