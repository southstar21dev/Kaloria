package dev.southstar21.kaloria.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.southstar21.kaloria.core.datastore.UserPreferences
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.designsystem.component.GlassStyle
import dev.southstar21.kaloria.core.designsystem.component.GreetingHeader
import dev.southstar21.kaloria.core.designsystem.component.LocalDockPadding
import dev.southstar21.kaloria.core.designsystem.component.SectionHeader
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.designsystem.component.grouped
import dev.southstar21.kaloria.core.designsystem.component.introItem
import dev.southstar21.kaloria.core.designsystem.component.rememberIntro
import dev.southstar21.kaloria.core.ui.EndCursorTextField

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
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
        onBack = onBack,
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
    onBack: () -> Unit,
) {
    val dock = LocalDockPadding.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val f = goals.form
    val intro = rememberIntro()
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().glassSource(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = dock + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp).glass(CircleShape, GlassStyle.Card)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back), Modifier.size(20.dp))
                }
                GreetingHeader(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle))
            }
        }
        item(key = "account") { Box(Modifier.introItem(intro, 0)) { account() } }
        item(key = "goals-header") { SectionHeader(stringResource(R.string.settings_goals_header)) }
        item(key = "goals") {
            // Tiles you type straight into: tap a number to change it, leave it empty for no target.
            Column(Modifier.introItem(intro, 1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GoalTile(stringResource(R.string.settings_goal_energy), f.energyKcal, { v -> onEditGoals { copy(energyKcal = v) } }, "kcal", Modifier.fillMaxWidth(), large = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoalTile(stringResource(R.string.settings_goal_protein), f.protein, { v -> onEditGoals { copy(protein = v) } }, "g", Modifier.weight(1f))
                    GoalTile(stringResource(R.string.settings_goal_carbs), f.carbohydrates, { v -> onEditGoals { copy(carbohydrates = v) } }, "g", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoalTile(stringResource(R.string.settings_goal_fat), f.fat, { v -> onEditGoals { copy(fat = v) } }, "g", Modifier.weight(1f))
                    GoalTile(stringResource(R.string.settings_goal_fiber), f.fiber, { v -> onEditGoals { copy(fiber = v) } }, "g", Modifier.weight(1f))
                }
                GoalTile(stringResource(R.string.settings_goal_steps), f.steps, { v -> onEditGoals { copy(steps = v) } }, stringResource(R.string.settings_goal_steps_unit), Modifier.fillMaxWidth(), last = true)
                Text(
                    buildString {
                        append(stringResource(R.string.settings_goals_note))
                        f.macroKcal?.let { append(stringResource(R.string.settings_goals_macro_kcal, it.grouped())) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                AnimatedVisibility(goals.changed) {
                    Button(onClick = onSaveGoals, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_goals_save)) }
                }
            }
        }
        item(key = "appearance-header") { SectionHeader(stringResource(R.string.settings_appearance_header)) }
        item(key = "appearance") {
            ContentCard(Modifier.introItem(intro, 2).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_reduce_transparency), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_reduce_transparency_body),
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

/** A goal as a big number you type into directly; empty means no target. */
@Composable
private fun GoalTile(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    last: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    val style = (if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall)
        .copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    ContentCard(modifier, onClick = { focus.requestFocus() }, iridescent = large) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            EndCursorTextField(
                value = value,
                onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
                textStyle = style,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (last) ImeAction.Done else ImeAction.Next),
                modifier = Modifier.focusRequester(focus).width(IntrinsicSize.Max),
                decorationBox = { field ->
                    Box {
                        if (value.isEmpty()) Text(stringResource(R.string.settings_goal_not_set), style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal))
                        field()
                    }
                },
            )
            if (value.isNotEmpty()) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = if (large) 6.dp else 3.dp),
                )
            }
        }
    }
}
