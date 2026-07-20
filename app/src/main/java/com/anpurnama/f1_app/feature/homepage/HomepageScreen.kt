package com.anpurnama.f1_app.feature.homepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.f1.model.Season
import com.anpurnama.f1_app.ui.theme.Spacing

/**
 * Homepage §2: season progress aggregates. Single section this slice;
 * §1 (favorite pager) and §3 (nearest GP info) land in slice 02
 * alongside the next two use cases.
 *
 * Wiring: [HomepageViewModel] is built from [F1App.wiring.getSeason] via
 * the existing `homepageViewModelFactory` — the function-ref seam from
 * [com.anpurnama.f1_app.core.Outcome] / [com.anpurnama.f1_app.f1].
 *
 * The screen maps the VM's [HomepageViewModel.UiState] into an
 * [Outcome] for [OutcomeContent] (the two are isomorphic; both stay
 * so the VM contract and tests are untouched).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomepageScreen(
    viewModel: HomepageViewModel = viewModel(
        factory = homepageViewModelFactory(
            (LocalContext.current.applicationContext as F1App).wiring.getSeason
        )
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val outcome: Outcome<Season> = when (val s = state) {
        is HomepageViewModel.UiState.Loading -> Outcome.Loading
        is HomepageViewModel.UiState.Success -> Outcome.Success(s.season)
        is HomepageViewModel.UiState.Failure -> Outcome.Failure(s.errorMessage)
    }

    PullToRefreshBox(
        isRefreshing = state is HomepageViewModel.UiState.Loading,
        onRefresh = { viewModel.refresh() },
    ) {
        OutcomeContent(
            outcome = outcome,
            onRetry = { viewModel.refresh() },
        ) { season ->
            SeasonProgressSection(season)
        }
    }
}

@Composable
private fun SeasonProgressSection(season: Season) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.normal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Season ${season.year}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ProgressCard(percent = (season.progressPercent * 100).toInt())
        StatCard(label = "GPs completed", value = season.completedGp.toString())
        StatCard(label = "Total km", value = season.totalKm.toString())
        StatCard(label = "Total laps", value = season.totalLaps.toString())
    }
}

@Composable
private fun ProgressCard(percent: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.normal)) {
            Text(
                text = "Progress",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.normal)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
