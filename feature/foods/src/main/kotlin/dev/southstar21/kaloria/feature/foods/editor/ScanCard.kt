package dev.southstar21.kaloria.feature.foods.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.designsystem.component.borderBeam
import dev.southstar21.kaloria.core.scanner.label.ExtractionMethod
import dev.southstar21.kaloria.feature.foods.R

/** The label photo next to the form, so every value can be checked against the original. */
@Composable
internal fun ScanCard(scan: ScanReview) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val bitmap = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, scan.photo) {
        value = scan.photo?.let { file ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                android.graphics.BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
            }
        }
    }.value
    // Fresh from the on-device AI: the beam plays around the card for a few seconds, then rests.
    var beaming by rememberSaveable { mutableStateOf(scan.method == ExtractionMethod.GeminiNano) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(6_000)
        beaming = false
    }
    ContentCard(Modifier.fillMaxWidth().borderBeam(cornerRadius = 28.dp, active = beaming), onClick = { expanded = !expanded }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (scan.method == ExtractionMethod.GeminiNano) Icons.Rounded.AutoAwesome else Icons.Rounded.TextFields,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (scan.method == ExtractionMethod.GeminiNano) stringResource(R.string.food_editor_scan_read_by_nano) else stringResource(R.string.food_editor_scan_read_by_rules),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(if (expanded) stringResource(R.string.food_editor_scan_smaller) else stringResource(R.string.food_editor_scan_enlarge), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                if (scan.warnings.isEmpty()) {
                    stringResource(R.string.food_editor_scan_check)
                } else {
                    stringResource(R.string.food_editor_scan_check_highlighted)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            bitmap?.let {
                androidx.compose.foundation.Image(
                    bitmap = it,
                    contentDescription = stringResource(R.string.food_editor_scan_photo),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (expanded) 520.dp else 220.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
                )
            }
        }
    }
}
