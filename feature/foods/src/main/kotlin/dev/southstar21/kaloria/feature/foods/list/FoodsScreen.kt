package dev.southstar21.kaloria.feature.foods.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.designsystem.component.GlassScreen
import dev.southstar21.kaloria.core.designsystem.component.LocalDockPadding
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.designsystem.component.introItem
import dev.southstar21.kaloria.core.designsystem.component.rememberIntro
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.ui.FoodRow
import dev.southstar21.kaloria.core.ui.gramsText
import dev.southstar21.kaloria.feature.foods.R

@Composable
fun FoodsRoute(onOpenFood: (String) -> Unit, viewModel: FoodsViewModel = hiltViewModel()) {
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    FoodsScreen(viewModel.query, { viewModel.query = it }, foods, onOpenFood)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FoodsScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    foods: List<Food>?,
    onOpenFood: (String) -> Unit,
) {
    val dock = LocalDockPadding.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var headerPx by remember { mutableIntStateOf(0) }
    val intro = rememberIntro()
    val header = with(LocalDensity.current) { headerPx.toDp() }
    GlassScreen {
        LazyColumn(
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = header + 12.dp, bottom = dock + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                foods == null -> item { Box(Modifier.fillMaxWidth(), Alignment.Center) { LoadingIndicator() } }

                foods.isEmpty() -> item {
                    ContentCard(Modifier.fillMaxWidth()) {
                        Text(if (query.isBlank()) stringResource(R.string.foods_empty_title) else stringResource(R.string.foods_no_matches_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (query.isBlank()) stringResource(R.string.foods_empty_text) else stringResource(R.string.foods_no_matches_text, query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> itemsIndexed(foods, key = { _, it -> it.id }) { index, food ->
                    FoodRow(
                        name = food.name,
                        brand = food.brand,
                        per100Label = stringResource(R.string.foods_per_100, food.unit.symbol),
                        kcalPer100 = food.nutrients.energyKcal,
                        source = food.source,
                        favorite = food.favorite,
                        macroLine = stringResource(R.string.foods_macro_line, food.nutrients.protein.gramsText(), food.nutrients.carbohydrates.gramsText(), food.nutrients.fat.gramsText()),
                        onClick = { onOpenFood(food.id) },
                        modifier = Modifier.animateItem().introItem(intro, index),
                    )
                }
            }
        }

        androidx.compose.foundation.layout.Column(
            Modifier
                .onSizeChanged { headerPx = it.height }
                .padding(start = 16.dp, end = 16.dp, top = top + 8.dp),
        ) {
            Text(stringResource(R.string.foods_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.foods_search_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().glass(),
            )
        }
    }
}
