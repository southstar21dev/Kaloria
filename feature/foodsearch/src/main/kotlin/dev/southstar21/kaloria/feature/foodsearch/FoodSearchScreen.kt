package dev.southstar21.kaloria.feature.foodsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.designsystem.component.GlassScreen
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.RemoteFood
import dev.southstar21.kaloria.core.model.Serving
import dev.southstar21.kaloria.core.ui.FoodRow
import dev.southstar21.kaloria.core.ui.PortionSheet
import dev.southstar21.kaloria.core.ui.gramsText
import dev.southstar21.kaloria.feature.foodsearch.R
import kotlinx.coroutines.launch

@Composable
fun FoodSearchRoute(
    key: FoodSearchKey,
    onBack: () -> Unit,
    onCreateFood: (name: String) -> Unit,
    onScanBarcode: (() -> Unit)?,
    viewModel: FoodSearchViewModel = hiltViewModel<FoodSearchViewModel, FoodSearchViewModel.Factory>(
        key = key.toString(),
        creationCallback = { it.create(key) },
    ),
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.logged.collect { name -> launch { snackbar.showSnackbar(resources.getString(R.string.food_search_logged, name)) } }
    }
    FoodSearchScreen(
        query = viewModel.query,
        onQueryChange = { viewModel.query = it },
        results = results,
        online = online,
        snackbar = snackbar,
        picked = remember(viewModel.pickedKey, results, online) { viewModel.pickIn(results, online) },
        onPick = viewModel::pick,
        defaultTime = viewModel::defaultTime,
        onLog = viewModel::log,
        onSaveServings = viewModel::saveServings,
        onBack = onBack,
        onCreateFood = onCreateFood,
        onScanBarcode = onScanBarcode,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FoodSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Food>?,
    online: OnlineState,
    snackbar: SnackbarHostState,
    picked: Pickable? = null,
    onPick: (Pickable?) -> Unit = {},
    defaultTime: () -> java.time.LocalTime,
    onLog: (Pickable, Double, java.time.LocalTime) -> Unit,
    onSaveServings: (foodId: String, List<Serving>) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onCreateFood: (String) -> Unit,
    onScanBarcode: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var barPx by remember { mutableIntStateOf(0) }
    val bar = with(LocalDensity.current) { barPx.toDp() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val ownSourceRefs = remember(results) { results.orEmpty().mapNotNull { it.sourceRef }.toSet() }
    // Online results minus foods already imported (those show under "Your foods"), worked out
    // once per change rather than each time the list content runs.
    val freshOnline = remember(online, ownSourceRefs) { online.withoutOwn(ownSourceRefs) }
    // The list content reads the query through these, so typing doesn't rebuild the whole list.
    val queryBlank = query.isBlank()
    val currentQuery = rememberUpdatedState(query)

    GlassScreen {
        LazyColumn(
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = bar, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (results != null && results.isNotEmpty()) {
                item(key = "own-header") { SectionHeader(stringResource(R.string.food_search_your_foods)) }
            }
            when {
                results == null -> item { Box(Modifier.fillMaxWidth(), Alignment.Center) { LoadingIndicator() } }

                results.isEmpty() && queryBlank -> item {
                    ContentCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.food_search_empty_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.food_search_empty_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> items(results, key = { "own:" + it.id }, contentType = { "own" }) { food ->
                    FoodRow(
                        name = food.name,
                        brand = food.brand,
                        per100Label = stringResource(R.string.food_search_per_100, food.unit.symbol),
                        kcalPer100 = food.nutrients.energyKcal,
                        source = food.source,
                        favorite = food.favorite,
                        macroLine = stringResource(R.string.food_search_macro_line, food.nutrients.protein.gramsText(), food.nutrients.carbohydrates.gramsText(), food.nutrients.fat.gramsText()),
                        onClick = {
                            keyboard?.hide()
                            onPick(Pickable.Own(food))
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            onlineSection(
                online = freshOnline,
                expanded = expanded,
                onToggle = { source -> expanded = if (source in expanded) expanded - source else expanded + source },
            ) { remote ->
                keyboard?.hide()
                onPick(Pickable.Online(remote))
            }
            item(key = "create") {
                ContentCard(Modifier.fillMaxWidth(), onClick = { onCreateFood(currentQuery.value.trim()) }, contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        val typed = currentQuery.value.trim()
                        Text(
                            if (typed.isEmpty()) stringResource(R.string.food_search_create_new) else stringResource(R.string.food_search_create_named, typed),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .onSizeChanged { barPx = it.height }
                .statusBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .glass()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.food_search_back)) }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.food_search_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Clear, stringResource(R.string.food_search_clear)) }
            }
            onScanBarcode?.let { scan ->
                IconButton(onClick = scan) { Icon(Icons.Rounded.QrCodeScanner, stringResource(R.string.food_search_scan_barcode)) }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 16.dp))
    }

    picked?.let { pick ->
        val (name, brand, unit, per100, servings) = when (pick) {
            is Pickable.Own -> with(pick.food) { SheetFood(name, brand, unit, nutrients, servings) }
            is Pickable.Online -> with(pick.food) { SheetFood(name, listOfNotNull(brand, detail).joinToString(" · ").ifEmpty { null }, unit, nutrients, servings) }
        }
        PortionSheet(
            title = name,
            subtitle = brand,
            unit = unit,
            per100 = per100,
            servings = servings,
            initialAmount = null,
            initialTime = defaultTime(),
            confirmLabel = stringResource(R.string.food_search_log),
            onConfirm = { amount, time ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onLog(pick, amount, time)
                onPick(null)
            },
            onDismiss = { onPick(null) },
            // Only foods already in the user's list can keep servings; online ones get them once saved.
            onServingsChange = (pick as? Pickable.Own)?.let { own -> { onSaveServings(own.food.id, it) } },
        )
    }
}

private data class SheetFood(
    val name: String,
    val brand: String?,
    val unit: dev.southstar21.kaloria.core.model.AmountUnit,
    val per100: dev.southstar21.kaloria.core.model.Nutrients,
    val servings: List<dev.southstar21.kaloria.core.model.Serving>,
)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

/** These results without foods the user already imported. */
private fun OnlineState.withoutOwn(ownSourceRefs: Set<String>): OnlineState = when (this) {
    is OnlineState.Loaded -> copy(
        results = results.copy(
            openFoodFacts = results.openFoodFacts.filter { it.sourceRef !in ownSourceRefs },
            usda = results.usda.filter { it.sourceRef !in ownSourceRefs },
        ),
    )

    else -> this
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.onlineSection(
    online: OnlineState,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onPick: (RemoteFood) -> Unit,
) {
    when (online) {
        OnlineState.Idle -> Unit

        OnlineState.Loading -> item(key = "online-loading") {
            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { LoadingIndicator() }
        }

        is OnlineState.Loaded -> {
            val r = online.results
            listOf(
                Triple(R.string.food_search_source_open_food_facts, r.openFoodFacts, FoodSource.OpenFoodFacts),
                Triple(R.string.food_search_source_usda, r.usda, FoodSource.Usda),
            ).forEach { (title, list, source) ->
                if (list.isNotEmpty() || source in r.failed) {
                    item(key = "header-$source") { SectionHeader(stringResource(title)) }
                }
                if (source in r.failed) {
                    item(key = "failed-$source") {
                        Text(
                            stringResource(R.string.food_search_source_failed, stringResource(title)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                // A few of each source up front, so generic USDA foods aren't buried under branded hits.
                val showAll = source.name in expanded
                items(if (showAll) list else list.take(COLLAPSED_RESULTS), key = { "$source:${it.sourceRef}" }, contentType = { "online" }) { food ->
                    FoodRow(
                        name = food.name,
                        brand = listOfNotNull(food.brand, food.detail).joinToString(" · ").ifEmpty { null },
                        per100Label = stringResource(R.string.food_search_per_100, food.unit.symbol),
                        kcalPer100 = food.nutrients.energyKcal,
                        source = food.source,
                        macroLine = stringResource(R.string.food_search_macro_line, food.nutrients.protein.gramsText(), food.nutrients.carbohydrates.gramsText(), food.nutrients.fat.gramsText()),
                        onClick = { onPick(food) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (list.size > COLLAPSED_RESULTS) {
                    item(key = "more-$source") {
                        TextButton(onClick = { onToggle(source.name) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAll) stringResource(R.string.food_search_show_fewer) else stringResource(R.string.food_search_show_all, list.size, stringResource(title)))
                        }
                    }
                }
            }
        }
    }
}

private const val COLLAPSED_RESULTS = 5
