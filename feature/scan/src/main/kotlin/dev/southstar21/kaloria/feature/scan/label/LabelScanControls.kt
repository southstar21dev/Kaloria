package dev.southstar21.kaloria.feature.scan.label

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.borderBeam
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.pressScale
import dev.southstar21.kaloria.core.scanner.label.NanoStatus
import dev.southstar21.kaloria.feature.scan.R

/** A camera shutter: a white ring around a white disc that presses in, dimmed while a photo is read. */
@Composable
internal fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val description = stringResource(R.string.label_scan_take_photo)
    val dim by animateFloatAsState(if (enabled) 1f else 0.4f, tween(200), label = "shutter")
    Box(
        Modifier
            .size(80.dp)
            .graphicsLayer { alpha = dim }
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .pressScale(interaction, pressedScale = 0.86f)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

/**
 * Gemini Nano's state, and the switch for it: with the model ready, a tap turns the AI off so the
 * label is read by the text rules alone (the fallback it uses anyway when the AI can't). The text
 * cross-fades and the chip resizes smoothly rather than swapping in place.
 */
@Composable
internal fun NanoChip(status: NanoStatus?, aiOn: Boolean, onToggle: () -> Unit, onDownload: () -> Unit) {
    val text = when (status) {
        NanoStatus.Available -> stringResource(if (aiOn) R.string.label_scan_ai_on else R.string.label_scan_ai_off)
        NanoStatus.Downloadable -> stringResource(R.string.label_scan_ai_download)
        NanoStatus.Downloading -> stringResource(R.string.label_scan_ai_downloading)
        NanoStatus.Unavailable -> stringResource(R.string.label_scan_ai_unavailable)
        null -> stringResource(R.string.label_scan_ai_checking)
    }
    val action = when (status) {
        NanoStatus.Available -> onToggle
        NanoStatus.Downloadable -> onDownload
        else -> null
    }
    val lit = status == NanoStatus.Available && aiOn
    val tint by animateColorAsState(if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(220), label = "ai")
    Row(
        Modifier
            .glass(CircleShape)
            // The AI at work: the beam rides the chip while it's on.
            .borderBeam(cornerRadius = 100.dp, active = lit, dark = true)
            .then(if (action != null) Modifier.clickable(role = Role.Switch, onClick = action) else Modifier)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 500f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = tint, modifier = Modifier.size(18.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            label = "ai-text",
        ) { Text(it, style = MaterialTheme.typography.labelLarge) }
    }
}
