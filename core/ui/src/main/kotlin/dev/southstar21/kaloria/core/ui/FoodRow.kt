package dev.southstar21.kaloria.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.model.FoodSource

/** One food in a list: name, brand and its per-100 energy and macros. */
@Composable
fun FoodRow(
    name: String,
    brand: String?,
    per100Label: String,
    kcalPer100: Double?,
    source: FoodSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    macroLine: String? = null,
) {
    GlassCard(modifier, onClick = onClick, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = source.icon(),
                contentDescription = source.label(),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (favorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Star, "Favorite", tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
                val subtitle = listOfNotNull(brand, macroLine).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(kcalPer100.kcalText(), style = MaterialTheme.typography.titleMedium)
                Text(
                    "kcal / $per100Label",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun FoodSource.icon() = when (this) {
    FoodSource.Label -> Icons.Rounded.CameraAlt
    FoodSource.OpenFoodFacts -> Icons.Rounded.Public
    FoodSource.Usda -> Icons.Rounded.Science
    FoodSource.Manual -> Icons.Rounded.Edit
}

fun FoodSource.label() = when (this) {
    FoodSource.Label -> "Scanned label"
    FoodSource.OpenFoodFacts -> "Open Food Facts"
    FoodSource.Usda -> "USDA"
    FoodSource.Manual -> "Entered manually"
}
