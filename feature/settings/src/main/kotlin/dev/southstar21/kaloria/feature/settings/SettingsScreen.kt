package dev.southstar21.kaloria.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.southstar21.kaloria.core.datastore.UserPreferences
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.LocalDockPadding
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.designsystem.component.introItem
import dev.southstar21.kaloria.core.designsystem.component.rememberIntro
import androidx.compose.foundation.layout.Box
import dev.southstar21.kaloria.core.ui.DecimalField

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val goals = viewModel.goals
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val account by viewModel.accountState.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    SettingsScreen(
        goals = goals,
        preferences = preferences,
        account = {
            AccountCard(
                state = account,
                syncing = syncing,
                form = viewModel.signIn,
                onEdit = viewModel::editSignIn,
                onSignIn = viewModel::signIn,
                onSignOut = viewModel::signOut,
                onSyncNow = viewModel::syncNow,
            )
        },
        onEditGoals = viewModel::editGoals,
        onSaveGoals = viewModel::saveGoals,
        onReduceTransparency = viewModel::setReduceTransparency,
    )
}

@Composable
internal fun SettingsScreen(
    goals: GoalsEditorState,
    preferences: UserPreferences?,
    account: @Composable () -> Unit,
    onEditGoals: (GoalsForm.() -> GoalsForm) -> Unit,
    onSaveGoals: () -> Unit,
    onReduceTransparency: (Boolean) -> Unit,
) {
    val dock = LocalDockPadding.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val f = goals.form
    val intro = rememberIntro()
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().glassSource(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = dock + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp))
        }
        item(key = "goals") {
            GlassCard(Modifier.introItem(intro, 0).fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily goals", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Your own numbers. Kaloria doesn't estimate them. Leave a field empty for no target.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DecimalField(f.energyKcal, { v -> onEditGoals { copy(energyKcal = v) } }, "Energy", Modifier.fillMaxWidth(), suffix = "kcal", integerOnly = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(f.protein, { v -> onEditGoals { copy(protein = v) } }, "Protein", Modifier.weight(1f), suffix = "g", integerOnly = true)
                        DecimalField(f.carbohydrates, { v -> onEditGoals { copy(carbohydrates = v) } }, "Carbs", Modifier.weight(1f), suffix = "g", integerOnly = true)
                        DecimalField(f.fat, { v -> onEditGoals { copy(fat = v) } }, "Fat", Modifier.weight(1f), suffix = "g", integerOnly = true)
                    }
                    f.macroKcal?.let { kcal ->
                        val target = f.energyKcal.toIntOrNull()
                        Text(
                            "Macro targets add up to ${"%,d".format(kcal)} kcal" + (target?.let { " (energy target ${"%,d".format(it)} kcal)" } ?: "") + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(f.fiber, { v -> onEditGoals { copy(fiber = v) } }, "Fiber", Modifier.weight(1f), suffix = "g", integerOnly = true)
                        DecimalField(f.waterMl, { v -> onEditGoals { copy(waterMl = v) } }, "Water", Modifier.weight(1f), suffix = "ml", integerOnly = true)
                    }
                    DecimalField(f.steps, { v -> onEditGoals { copy(steps = v) } }, "Steps", Modifier.fillMaxWidth(), suffix = "steps", integerOnly = true)
                    AnimatedVisibility(goals.changed) {
                        Button(onClick = onSaveGoals, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save goals") }
                    }
                }
            }
        }
        item(key = "account") { Box(Modifier.introItem(intro, 1)) { account() } }
        item(key = "appearance") {
            GlassCard(Modifier.introItem(intro, 2).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reduce transparency", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Frosted, more opaque glass instead of clear refracting glass. Easier to read, lighter on the battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = preferences?.reduceTransparency ?: false, onCheckedChange = onReduceTransparency)
                }
            }
        }
    }
}
