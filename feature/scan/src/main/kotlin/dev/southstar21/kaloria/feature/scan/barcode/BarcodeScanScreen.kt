package dev.southstar21.kaloria.feature.scan.barcode

import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.util.concurrent.Executors
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.southstar21.kaloria.core.data.repository.BarcodeResult
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.LocalGlassOverCamera
import androidx.compose.runtime.CompositionLocalProvider
import dev.southstar21.kaloria.core.scanner.BarcodeAnalyzer
import dev.southstar21.kaloria.core.ui.PortionSheet
import dev.southstar21.kaloria.feature.scan.BarcodeScanKey
import dev.southstar21.kaloria.feature.scan.camera.CameraPreview

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
    // ML Kit delivers results on the main thread, so the view model is called there.
    val analyzer = remember { BarcodeAnalyzer(viewModel::onBarcode) }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
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
        analysis = analysis,
        onBack = onBack,
        onRescan = viewModel::rescan,
        defaultTime = viewModel::defaultTime,
        onLog = viewModel::log,
        onScanLabel = onScanLabel,
        onEnterManually = onEnterManually,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BarcodeScanScreen(
    state: BarcodeState,
    analysis: ImageAnalysis,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    defaultTime: () -> java.time.LocalTime,
    onLog: (Double, java.time.LocalTime) -> Unit,
    onScanLabel: (String) -> Unit,
    onEnterManually: (String) -> Unit,
) {
    var camera by remember { mutableStateOf<Camera?>(null) }
    val haptics = LocalHapticFeedback.current
    // A buzz the moment a barcode is recognised.
    LaunchedEffect(state is BarcodeState.LookingUp) { if (state is BarcodeState.LookingUp) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    var torch by remember { mutableStateOf(false) }

    // Nothing can be captured behind the live camera, so controls use tinted, non-refracting glass.
    CompositionLocalProvider(LocalGlassOverCamera provides true) {
    Box(Modifier.fillMaxSize().padding(0.dp)) {
        CameraPreview(useCases = remember(analysis) { listOf(analysis) }, onCamera = { camera = it })

        // Aiming frame.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 280.dp, height = 160.dp)
                .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp)),
        )

        Row(
            Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glass()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            IconButton(
                onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                modifier = Modifier.glass(),
            ) { Icon(if (torch) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn, if (torch) "Torch off" else "Torch on") }
        }

        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
            when (state) {
                BarcodeState.Scanning -> GlassCard(Modifier.fillMaxWidth()) {
                    Text("Point at a barcode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your own foods are checked first, then Open Food Facts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is BarcodeState.LookingUp -> GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LoadingIndicator()
                        Text("Looking up ${state.code}…", style = MaterialTheme.typography.titleMedium)
                    }
                }
                is BarcodeState.Found -> when (state.result) {
                    BarcodeResult.NotFound, BarcodeResult.Offline -> NotFoundCard(
                        code = state.code,
                        offline = state.result == BarcodeResult.Offline,
                        onScanLabel = { onScanLabel(state.code) },
                        onEnterManually = { onEnterManually(state.code) },
                        onRescan = onRescan,
                    )
                    else -> Unit
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
            onConfirm = { amount, time -> haptics.performHapticFeedback(HapticFeedbackType.Confirm); onLog(amount, time) },
            onDismiss = onRescan,
        )
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
private fun NotFoundCard(code: String, offline: Boolean, onScanLabel: () -> Unit, onEnterManually: () -> Unit, onRescan: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (offline) "Couldn't check Open Food Facts" else "$code isn't in any database yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (offline) "You're offline. Scan the label to add it to your own foods now." else "Scan its nutrition label once and it's yours from then on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onScanLabel, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Scan nutrition label") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEnterManually, modifier = Modifier.weight(1f)) { Text("Enter manually") }
                OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) { Text("Scan again") }
            }
        }
    }
}
