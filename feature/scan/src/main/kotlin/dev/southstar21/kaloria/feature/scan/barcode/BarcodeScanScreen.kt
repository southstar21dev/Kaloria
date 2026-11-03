package dev.southstar21.kaloria.feature.scan.barcode

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.southstar21.kaloria.core.data.repository.BarcodeResult
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.GlassOverCamera
import dev.southstar21.kaloria.core.designsystem.component.OrbState
import dev.southstar21.kaloria.core.designsystem.component.ThinkingOrb
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.scanner.BarcodeAnalyzer
import dev.southstar21.kaloria.core.scanner.BarcodeSighting
import dev.southstar21.kaloria.core.ui.PortionSheet
import dev.southstar21.kaloria.feature.scan.BarcodeScanKey
import dev.southstar21.kaloria.feature.scan.camera.CameraPreview
import dev.southstar21.kaloria.feature.scan.camera.ScanFrame
import dev.southstar21.kaloria.feature.scan.camera.toView
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

@Composable
fun BarcodeScanRoute(
    key: BarcodeScanKey,
    onBack: () -> Unit,
    onLogged: (String) -> Unit,
    onScanLabel: (barcode: String) -> Unit,
    onEnterManually: (barcode: String) -> Unit,
    viewModel: BarcodeScanViewModel = hiltViewModel<BarcodeScanViewModel, BarcodeScanViewModel.Factory>(
        key = key.toString(),
        creationCallback = { it.create(key) },
    ),
) {
    LaunchedEffect(viewModel) { viewModel.done.collect(onLogged) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    // ML Kit delivers results on the main thread, so the view model and the frame are updated there.
    var sighting by remember { mutableStateOf<BarcodeSighting?>(null) }
    val analyzer = remember { BarcodeAnalyzer(viewModel::onBarcode) { sighting = it } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // 1080p in the preview's shape: small or distant codes need the pixels per bar, and ML Kit
            // still reads every frame at 30 fps (about 17 ms each on a Galaxy S-class phone).
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
            .apply { setAnalyzer(executor, analyzer) }
    }
    DisposableEffect(Unit) {
        onDispose {
            analysis.clearAnalyzer()
            analyzer.close()
            executor.shutdown()
        }
    }

    BarcodeScanScreen(
        state = viewModel.state,
        sighting = sighting,
        analysis = analysis,
        onCamera = { cam ->
            camera = cam
            analyzer.onZoom = { ratio ->
                val max = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                cam.cameraControl.setZoomRatio(ratio.coerceAtMost(max))
                max > 1f
            }
        },
        onBack = onBack,
        onRescan = {
            // ML Kit only ever zooms in; back to 1x so the next product can be held close again.
            camera?.cameraControl?.setZoomRatio(1f)
            viewModel.rescan()
        },
        defaultTime = viewModel::defaultTime,
        onLog = viewModel::log,
        onScanLabel = onScanLabel,
        onEnterManually = onEnterManually,
        onSaveServings = viewModel::saveServings,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BarcodeScanScreen(
    state: BarcodeState,
    sighting: BarcodeSighting?,
    analysis: ImageAnalysis,
    onCamera: (Camera) -> Unit,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    defaultTime: () -> java.time.LocalTime,
    onLog: (Double, java.time.LocalTime) -> Unit,
    onScanLabel: (String) -> Unit,
    onEnterManually: (String) -> Unit,
    onSaveServings: (foodId: String, List<dev.southstar21.kaloria.core.model.Serving>) -> Unit = { _, _ -> },
) {
    var camera by remember { mutableStateOf<Camera?>(null) }
    val haptics = LocalHapticFeedback.current
    // A buzz the moment a barcode is recognised.
    LaunchedEffect(state is BarcodeState.LookingUp) { if (state is BarcodeState.LookingUp) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    var torch by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(IntSize.Zero) }

    // The frame follows the code while scanning, holds on it once read, and lets go a moment after
    // it leaves the view (a frame or two without it is just a blur, not gone). It moves only once a
    // code has been read in two frames running, and eases halfway toward each new reading, so a
    // stray detection can't fling it across the screen.
    val scanning = state == BarcodeState.Scanning
    var target by remember { mutableStateOf<Rect?>(null) }
    var streak by remember { mutableIntStateOf(0) }
    LaunchedEffect(sighting, scanning, view) {
        val seen = sighting?.takeIf { view != IntSize.Zero }?.toView(view)
        if (!scanning) {
            // Read on its very first sighting: lock straight onto it.
            if (target == null) target = seen
            return@LaunchedEffect
        }
        if (seen != null) {
            streak++
            if (streak >= 2) target = target?.let { lerp(it, seen, 0.5f) } ?: seen
        } else {
            streak = 0
            delay(450)
            target = null
        }
    }
    LaunchedEffect(scanning) {
        if (scanning) {
            target = null
            streak = 0
        }
    }
    // Continuous autofocus favours whatever fills the view, often the background. While nothing has
    // been read for a few seconds, refocus on the middle, where the code is held.
    LaunchedEffect(camera, scanning, sighting == null) {
        val cam = camera ?: return@LaunchedEffect
        while (scanning && sighting == null) {
            delay(3_000)
            val centre = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
            cam.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(centre, FocusMeteringAction.FLAG_AF).setAutoCancelDuration(2, TimeUnit.SECONDS).build(),
            )
        }
    }

    // Nothing can be captured behind the live camera, so controls use dark tinted, non-refracting glass.
    GlassOverCamera {
        Box(Modifier.fillMaxSize().onSizeChanged { view = it }) {
            CameraPreview(useCases = remember(analysis) { listOf(analysis) }, onCamera = {
                camera = it
                onCamera(it)
            })

            ScanFrame(target = target, restSize = DpSize(280.dp, 170.dp), locked = !scanning)

            // The digits, pinned under the code once it's read.
            val code = (state as? BarcodeState.LookingUp)?.code ?: (state as? BarcodeState.Found)?.code
            AnimatedVisibility(
                visible = code != null && target != null,
                enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.8f),
                exit = fadeOut(tween(150)),
                modifier = Modifier.offset {
                    val t = target ?: Rect.Zero
                    IntOffset((t.center.x).toInt(), (t.bottom + 28.dp.toPx()).toInt())
                },
            ) {
                Text(
                    code.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            // Centred on the offset point.
                            val p = measurable.measure(constraints)
                            layout(p.width, p.height) { p.place(-p.width / 2, 0) }
                        }
                        .glass(CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            Row(
                Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).glass(CircleShape)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text(
                    "Scan barcode",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.glass(CircleShape).padding(horizontal = 18.dp, vertical = 12.dp),
                )
                IconButton(
                    onClick = {
                        torch = !torch
                        camera?.cameraControl?.enableTorch(torch)
                    },
                    modifier = Modifier.size(48.dp).glass(CircleShape, tint = if (torch) Color.White else Color.Unspecified),
                ) {
                    Icon(
                        if (torch) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn,
                        if (torch) "Torch off" else "Torch on",
                        tint = if (torch) Color.Black else LocalContentColor.current,
                    )
                }
            }

            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
                AnimatedContent(
                    targetState = state.cardKey(),
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) { it / 4 })
                            .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 6 })
                            .using(SizeTransform(clip = false))
                    },
                    label = "card",
                ) { key ->
                    when (key) {
                        CardKey.Scanning -> GlassCard(Modifier.fillMaxWidth()) {
                            Text("Point at a barcode", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Hold it steady inside the frame. Your own foods are checked first, then Open Food Facts.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        CardKey.LookingUp -> GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                ThinkingOrb(OrbState.Searching, size = 40.dp, dark = true)
                                Column {
                                    Text("Looking it up…", style = MaterialTheme.typography.titleMedium)
                                    Text("Your foods, then Open Food Facts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        CardKey.NotFound, CardKey.Offline, CardKey.Unavailable -> (state as? BarcodeState.Found)?.let { found ->
                            NotFoundCard(
                                code = found.code,
                                problem = key,
                                onScanLabel = { onScanLabel(found.code) },
                                onEnterManually = { onEnterManually(found.code) },
                                onRescan = onRescan,
                            )
                        }

                        CardKey.None -> Unit
                    }
                }
            }
        }
    }

    val found = (state as? BarcodeState.Found)?.result
    val sheetFood = when (found) {
        is BarcodeResult.Own -> with(found.food) { SheetData(name, brand, unit, nutrients, servings) }
        is BarcodeResult.Online -> with(found.food) { SheetData(name, listOfNotNull(brand, "Open Food Facts").joinToString(" · "), unit, nutrients, servings) }
        else -> null
    }
    sheetFood?.let { f ->
        PortionSheet(
            title = f.name,
            subtitle = f.brand,
            unit = f.unit,
            per100 = f.per100,
            servings = f.servings,
            initialAmount = null,
            initialTime = defaultTime(),
            confirmLabel = "Log",
            onConfirm = { amount, time ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onLog(amount, time)
            },
            onDismiss = onRescan,
            // Only the user's own foods keep servings; Open Food Facts ones get them once logged and saved.
            onServingsChange = (found as? BarcodeResult.Own)?.let { own -> { onSaveServings(own.food.id, it) } },
        )
    }
}

/** Which bottom card shows; the found-food case is the portion sheet instead. */
private enum class CardKey { Scanning, LookingUp, NotFound, Offline, Unavailable, None }

private fun BarcodeState.cardKey() = when (this) {
    BarcodeState.Scanning -> CardKey.Scanning

    is BarcodeState.LookingUp -> CardKey.LookingUp

    is BarcodeState.Found -> when (result) {
        BarcodeResult.NotFound -> CardKey.NotFound
        BarcodeResult.Offline -> CardKey.Offline
        BarcodeResult.Unavailable -> CardKey.Unavailable
        else -> CardKey.None
    }
}

private data class SheetData(
    val name: String,
    val brand: String?,
    val unit: dev.southstar21.kaloria.core.model.AmountUnit,
    val per100: dev.southstar21.kaloria.core.model.Nutrients,
    val servings: List<dev.southstar21.kaloria.core.model.Serving>,
)

@Composable
private fun NotFoundCard(code: String, problem: CardKey, onScanLabel: () -> Unit, onEnterManually: () -> Unit, onRescan: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when (problem) {
                    CardKey.Offline -> "You're offline"
                    CardKey.Unavailable -> "Open Food Facts didn't answer"
                    else -> "$code isn't in any database yet"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                when (problem) {
                    CardKey.Offline -> "Scan the label to add it to your own foods now, or try again once you're connected."
                    CardKey.Unavailable -> "It may be busy for a moment. Try again, or scan the label to add it yourself."
                    else -> "Scan its nutrition label once and it's yours from then on."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onScanLabel, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Scan nutrition label") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEnterManually, modifier = Modifier.weight(1f)) { Text("Enter manually") }
                OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) { Text(if (problem == CardKey.NotFound) "Scan again" else "Try again") }
            }
        }
    }
}
