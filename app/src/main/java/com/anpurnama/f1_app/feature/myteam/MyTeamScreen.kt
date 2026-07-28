package com.anpurnama.f1_app.feature.myteam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.anpurnama.f1_app.F1App
import com.anpurnama.f1_app.core.ui.OutcomeContent
import com.anpurnama.f1_app.core.ui.SectionUiState
import com.anpurnama.f1_app.f1.data.Seasons
import com.anpurnama.f1_app.f1.data.driverImageUrl
import com.anpurnama.f1_app.f1.data.teamImageUrl
import com.anpurnama.f1_app.f1.model.ConstructorStanding
import com.anpurnama.f1_app.f1.model.DriverStanding
import com.anpurnama.f1_app.feature.favorites.Favorites
import com.anpurnama.f1_app.ui.theme.Spacing
import com.anpurnama.f1_app.ui.theme.TeamColors

@Composable
fun MyTeamScreen(
    viewModel: MyTeamViewModel = rememberMyTeamViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MyTeamContent(
        state = state,
        onSelectDriver = viewModel::selectDriver,
        onSelectTeam = viewModel::selectTeam,
        onRetry = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MyTeamContent(
    state: MyTeamViewModel.UiState,
    onSelectDriver: (DriverSlot, String) -> Unit,
    onSelectTeam: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerSlot by rememberSaveable { mutableStateOf<PickerSlot?>(null) }
    val drivers = (state.drivers as? SectionUiState.Content)?.data.orEmpty()
    val constructors = (state.constructors as? SectionUiState.Content)?.data.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.normal, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "My Team",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Choose two drivers and one constructor. Your homepage updates automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutcomeContent(
            state = state.favorites,
            modifier = Modifier.fillMaxWidth(),
        ) { favorites ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FavoriteSlotCard(
                    label = "Driver 1",
                    favoriteId = favorites.driver1Id,
                    name = driverName(favorites.driver1Id, drivers),
                    detail = driverDetail(favorites.driver1Id, drivers),
                    teamId = driverTeamId(favorites.driver1Id, drivers),
                    onClick = { pickerSlot = PickerSlot.Driver1 },
                    driverName = driverStanding(favorites.driver1Id, drivers)?.name,
                    driverSurname = driverStanding(favorites.driver1Id, drivers)?.surname,
                )
                FavoriteSlotCard(
                    label = "Driver 2",
                    favoriteId = favorites.driver2Id,
                    name = driverName(favorites.driver2Id, drivers),
                    detail = driverDetail(favorites.driver2Id, drivers),
                    teamId = driverTeamId(favorites.driver2Id, drivers),
                    onClick = { pickerSlot = PickerSlot.Driver2 },
                    driverName = driverStanding(favorites.driver2Id, drivers)?.name,
                    driverSurname = driverStanding(favorites.driver2Id, drivers)?.surname,
                )
                FavoriteSlotCard(
                    label = "Constructor",
                    favoriteId = favorites.teamId,
                    name = constructorName(favorites.teamId, constructors),
                    detail = constructors.firstOrNull { it.teamId == favorites.teamId }?.country,
                    teamId = favorites.teamId,
                    onClick = { pickerSlot = PickerSlot.Constructor },
                )
            }
        }
    }

    val activePicker = pickerSlot
    if (activePicker != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { pickerSlot = null },
            sheetState = sheetState,
        ) {
            val favorites = (state.favorites as? SectionUiState.Content)?.data
            PickerContent(
                slot = activePicker,
                favorites = favorites,
                drivers = state.drivers,
                constructors = state.constructors,
                onRetry = onRetry,
                onDriverClick = { slot, driverId ->
                    onSelectDriver(slot, driverId)
                    pickerSlot = null
                },
                onTeamClick = { teamId ->
                    onSelectTeam(teamId)
                    pickerSlot = null
                },
            )
        }
    }
}

private enum class PickerSlot(val title: String) {
    Driver1("Choose Driver 1"),
    Driver2("Choose Driver 2"),
    Constructor("Choose Constructor"),
}

@Composable
private fun FavoriteSlotCard(
    label: String,
    favoriteId: String?,
    name: String,
    detail: String?,
    teamId: String?,
    onClick: () -> Unit,
    driverName: String? = null,
    driverSurname: String? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = if (favoriteId == null) {
                    "Choose favorite $label"
                } else {
                    "Change favorite $label"
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.normal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.normal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val year = Seasons.currentSeasonYear()
            val imageUrl = if (teamId == null) {
                null
            } else if (driverName != null && driverSurname != null) {
                driverImageUrl(driverName, driverSurname, teamId, year)
            } else {
                teamImageUrl(teamId, year)
            }
            if (imageUrl != null) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(size = 16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (favoriteId == null) "Choose" else "Change",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PickerContent(
    slot: PickerSlot,
    favorites: Favorites?,
    drivers: SectionUiState<List<DriverStanding>>,
    constructors: SectionUiState<List<ConstructorStanding>>,
    onRetry: () -> Unit,
    onDriverClick: (DriverSlot, String) -> Unit,
    onTeamClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.normal)
            .padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.normal),
    ) {
        Text(
            text = slot.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        when (slot) {
            PickerSlot.Driver1,
            PickerSlot.Driver2,
            -> OutcomeContent(
                state = drivers,
                modifier = Modifier.heightIn(min = 160.dp, max = 520.dp),
                onRetry = onRetry,
            ) { rows ->
                val driverSlot = if (slot == PickerSlot.Driver1) DriverSlot.Driver1 else DriverSlot.Driver2
                val otherDriverId = if (driverSlot == DriverSlot.Driver1) {
                    favorites?.driver2Id
                } else {
                    favorites?.driver1Id
                }
                PickerList(rows, key = DriverStanding::driverId) { driver ->
                    val usedByOtherSlot = driver.driverId == otherDriverId
                    PickerRow(
                        name = driver.driverName.ifBlank { driver.driverId },
                        detail = listOfNotNull(driver.driverShortName, driver.teamName)
                            .joinToString(" · "),
                        enabled = !usedByOtherSlot,
                        status = if (usedByOtherSlot) "Already selected" else null,
                        onClick = { onDriverClick(driverSlot, driver.driverId) },
                    )
                }
            }
            PickerSlot.Constructor -> OutcomeContent(
                state = constructors,
                modifier = Modifier.heightIn(min = 160.dp, max = 520.dp),
                onRetry = onRetry,
            ) { rows ->
                PickerList(rows, key = ConstructorStanding::teamId) { constructor ->
                    PickerRow(
                        name = constructor.teamName.ifBlank { constructor.teamId },
                        detail = constructor.country.orEmpty(),
                        enabled = true,
                        status = null,
                        onClick = { onTeamClick(constructor.teamId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> PickerList(
    rows: List<T>,
    key: (T) -> Any,
    content: @Composable (T) -> Unit,
) {
    if (rows.isEmpty()) {
        Text(
            text = "No current standings",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(
            modifier = Modifier.heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(rows, key = key) { row -> content(row) }
        }
    }
}

@Composable
private fun PickerRow(
    name: String,
    detail: String,
    enabled: Boolean,
    status: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.normal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.normal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun driverName(id: String?, drivers: List<DriverStanding>): String = when {
    id == null -> "Add a driver"
    else -> drivers.firstOrNull { it.driverId == id }?.driverName?.ifBlank { id } ?: id
}

private fun driverDetail(id: String?, drivers: List<DriverStanding>): String? =
    drivers.firstOrNull { it.driverId == id }?.let { driver ->
        listOfNotNull(driver.driverShortName, driver.teamName).joinToString(" · ")
    }

private fun driverTeamId(id: String?, drivers: List<DriverStanding>): String? =
    drivers.firstOrNull { it.driverId == id }?.teamId

private fun driverStanding(id: String?, drivers: List<DriverStanding>): DriverStanding? =
    drivers.firstOrNull { it.driverId == id }

private fun constructorName(id: String?, constructors: List<ConstructorStanding>): String = when {
    id == null -> "Add a constructor"
    else -> constructors.firstOrNull { it.teamId == id }?.teamName?.ifBlank { id } ?: id
}

@Composable
private fun rememberMyTeamViewModel(): MyTeamViewModel {
    val wiring = (LocalContext.current.applicationContext as F1App).wiring
    return viewModel(
        factory = myTeamViewModelFactory(
            getDriversStandings = wiring.getDriversStandings,
            getConstructorsStandings = wiring.getConstructorsStandings,
            favoritesCache = wiring.favoritesCache,
            currentSeasonResourcesCacheRepository = wiring.currentSeasonResourcesCacheRepository,
        ),
    )
}
