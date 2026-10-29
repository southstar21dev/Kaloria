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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.GlassScreen
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.RemoteFood
import dev.southstar21.kaloria.core.ui.FoodRow
import dev.southstar21.kaloria.core.ui.PortionSheet
import dev.southstar21.kaloria.core.ui.gramsText
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
    LaunchedEffect(viewModel) {
        viewModel.logged.collect { name -> launch { snackbar.showSnackbar("Logged $name") } }
    }
    FoodSearchScreen(
        query = viewModel.query,
        onQueryChange = { viewModel.query = it },
        results = results,
        online = online,
        snackbar = snackbar,
        defaultTime = viewModel::defaultTime,
        onLog = viewModel::log,
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
    defaultTime: () -> java.time.LocalTime,
    onLog: (Pickable, Double, java.time.LocalTime) -> Unit,
    onBack: () -> Unit,
    onCreateFood: (String) -> Unit,
    onScanBarcode: (() -> Unit)?,
) {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var barPx by remember { mutableIntStateOf(0) }
    val bar = with(LocalDensity.current) { barPx.toDp() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    GlassScreen {
        LazyColumn(
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = bar, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (results != null && results.isNotEmpty()) {
                item(key = "own-header") { SectionHeader("Your foods") }
            }
            when {
                results == null -> item { Box(Modifier.fillMaxWidth(), Alignment.Center) { LoadingIndicator() } }
                results.isEmpty() && query.isBlank() -> item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Your food database is empty", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Search above to find foods online, or create your own.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> items(results, key = { "own:" + it.id }) { food ->
                    FoodRow(
                        name = food.name,
                        brand = food.brand,
                        per100Label = "100 ${food.unit.symbol}",
                        kcalPer100 = food.nutrients.energyKcal,
                        source = food.source,
                        favorite = food.favorite,
                        macroLine = "P ${food.nutrients.protein.gramsText()} · C ${food.nutrients.carbohydrates.gramsText()} · F ${food.nutrients.fat.gramsText()}",
                        onClick = { keyboard?.hide(); selectedKey = Pickable.Own(food).key },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            onlineSection(
                online = online,
                ownSourceRefs = results.orEmpty().mapNotNull { it.sourceRef }.toSet(),
                expanded = expanded,
                onToggle = { source -> expanded = if (source in expanded) expanded - source else expanded + source },
            ) { remote ->
                keyboard?.hide()
                selectedKey = Pickable.Online(remote).key
            }
            item(key = "create") {
                GlassCard(Modifier.fillMaxWidth(), onClick = { onCreateFood(query.trim()) }, contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            if (query.isBlank()) "Create a new food" else "Create “${query.trim()}”",
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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search foods") },
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
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Clear, "Clear") }
            }
            onScanBarcode?.let { scan ->
                IconButton(onClick = scan) { Icon(Icons.Rounded.QrCodeScanner, "Scan barcode") }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 16.dp))
    }

    val picks = results.orEmpty().map(Pickable::Own) + (online as? OnlineState.Loaded)?.results?.let {
        (it.openFoodFacts + it.usda).map(Pickable::Online)
    }.orEmpty()
    picks.firstOrNull { it.key == selectedKey }?.let { pick ->
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
            confirmLabel = "Log",
            onConfirm = { amount, time ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onLog(pick, amount, time); selectedKey = null
            },
            onDismiss = { selectedKey = null },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.onlineSection(
    online: OnlineState,
    ownSourceRefs: Set<String>,
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
            // Foods already imported show up under "Your foods" instead.
            fun fresh(list: List<RemoteFood>) = list.filter { it.sourceRef !in ownSourceRefs }
            listOf(
                Triple("Open Food Facts", fresh(r.openFoodFacts), FoodSource.OpenFoodFacts),
                Triple("USDA generic foods", fresh(r.usda), FoodSource.Usda),
            ).forEach { (title, list, source) ->
                if (list.isNotEmpty() || source in r.failed) {
                    item(key = "header-$source") { SectionHeader(title) }
                }
                if (source in r.failed) {
                    item(key = "failed-$source") {
                        Text(
                            "Couldn't load results from $title. Check your connection or try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                // A few of each source up front, so generic USDA foods aren't buried under branded hits.
                val showAll = source.name in expanded
                items(if (showAll) list else list.take(COLLAPSED_RESULTS), key = { "$source:${it.sourceRef}" }) { food ->
                    FoodRow(
                        name = food.name,
                        brand = listOfNotNull(food.brand, food.detail).joinToString(" · ").ifEmpty { null },
                        per100Label = "100 ${food.unit.symbol}",
                        kcalPer100 = food.nutrients.energyKcal,
                        source = food.source,
                        macroLine = "P ${food.nutrients.protein.gramsText()} · C ${food.nutrients.carbohydrates.gramsText()} · F ${food.nutrients.fat.gramsText()}",
                        onClick = { onPick(food) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (list.size > COLLAPSED_RESULTS) {
                    item(key = "more-$source") {
                        TextButton(onClick = { onToggle(source.name) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAll) "Show fewer" else "Show all ${list.size} from $title")
                        }
                    }
                }
            }
        }
    }
}

private const val COLLAPSED_RESULTS = 5
