package dev.southstar21.kaloria.feature.scan.label

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.GlassOverCamera
import dev.southstar21.kaloria.core.designsystem.component.OrbState
import dev.southstar21.kaloria.core.designsystem.component.ThinkingOrb
import dev.southstar21.kaloria.core.designsystem.component.borderBeam
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.scanner.label.NanoStatus
import dev.southstar21.kaloria.feature.scan.LabelScanKey
import dev.southstar21.kaloria.feature.scan.R
import dev.southstar21.kaloria.feature.scan.camera.CameraPreview
import dev.southstar21.kaloria.feature.scan.camera.ScanFrame
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

@Composable
fun LabelScanRoute(
    key: LabelScanKey,
    onBack: () -> Unit,
    onReview: (draftId: String, barcode: String?) -> Unit,
    viewModel: LabelScanViewModel = hiltViewModel<LabelScanViewModel, LabelScanViewModel.Factory>(
        key = key.toString(),
        creationCallback = { it.create(key) },
    ),
) {
    LaunchedEffect(viewModel) { viewModel.draftReady.collect { onReview(it, key.barcode) } }
    val context = LocalContext.current
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            // A full 12 MP frame: the table is cut out of it, so its small print needs the pixels.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(MAX_SIDE, MAX_SIDE * 3 / 4), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
    }
    // Frames for finding the table live, at the preview's size: enough to read a table's words.
    val analysis = remember {
        ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build(),
            )
            .build()
    }
    DisposableEffect(analysis) {
        val worker = Executors.newSingleThreadExecutor()
        analysis.setAnalyzer(worker, viewModel.analyzer)
        onDispose {
            analysis.clearAnalyzer()
            worker.shutdown()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.read {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
    }

    LabelScanScreen(
        viewModel = viewModel,
        capture = capture,
        analysis = analysis,
        onBack = onBack,
        onShutter = {
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        if (!viewModel.read { image.use { it.toUprightBitmap() } }) image.close()
                    }

                    override fun onError(exception: ImageCaptureException) = Unit
                },
            )
        },
        onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

/**
 * Longest side of the photo kept for reading: about the sensor's 12 MP. The table is found on a
 * smaller copy, but cut from this, so a label that fills only part of the frame stays legible.
 */
private const val MAX_SIDE = 4096

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val scale = (MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(imageInfo.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LabelScanScreen(
    viewModel: LabelScanViewModel,
    capture: ImageCapture,
    analysis: ImageAnalysis,
    onBack: () -> Unit,
    onShutter: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    val state = viewModel.state
    val reading = state == LabelScanState.Reading
    // A white flash the instant the photo is taken, like a camera shutter.
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shoot = {
        scope.launch {
            flash.snapTo(0.55f)
            flash.animateTo(0f, tween(380))
        }
        onShutter()
    }
    // Nothing can be captured behind the live camera, so controls use dark tinted, non-refracting glass.
    GlassOverCamera {
        Box(Modifier.fillMaxSize()) {
            CameraPreview(useCases = remember(capture, analysis) { listOf(capture, analysis) })

            // The resting frame until a table is found; then the table's own outline takes over.
            val live = viewModel.live
            val resting by animateFloatAsState(if (live == null) 1f else 0f, tween(260), label = "resting")
            ScanFrame(
                target = null,
                restSize = DpSize(300.dp, 420.dp),
                cornerRadius = 28.dp,
                locked = reading,
                modifier = Modifier.graphicsLayer { alpha = resting },
            )
            LiveOutline(live)

            FlatTablePreview(viewModel.flattened, Modifier.align(Alignment.Center))

            Row(
                Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).glass(CircleShape)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.label_scan_back)) }
                NanoChip(viewModel.nano, aiOn = !viewModel.rulesOnly, onToggle = { viewModel.rulesOnly = !viewModel.rulesOnly }, onDownload = viewModel::downloadModel)
            }

            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // The latest flat preview, kept while its card fades out.
                var preview by remember { mutableStateOf<Bitmap?>(null) }
                live?.let { preview = it.flat }
                AnimatedContent(
                    targetState = when {
                        reading -> Hint.Reading
                        live != null -> Hint.Found
                        else -> Hint.Aim
                    },
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) { it / 4 })
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "hint",
                ) { hint ->
                    if (hint == Hint.Found) {
                        LiveFlatCard(preview)
                    } else if (hint == Hint.Reading) {
                        GlassCard(Modifier.borderBeam(cornerRadius = 28.dp, dark = true)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                ThinkingOrb(OrbState.Working, size = 40.dp, dark = true)
                                Text(
                                    stringResource(if (viewModel.nano == NanoStatus.Available && !viewModel.rulesOnly) R.string.label_scan_reading_nano else R.string.label_scan_reading_text),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    } else {
                        GlassCard(contentPadding = 14.dp) {
                            Text(
                                stringResource(R.string.label_scan_aim_hint),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    IconButton(onClick = onPickPhoto, modifier = Modifier.size(52.dp).glass(CircleShape)) {
                        Icon(Icons.Rounded.PhotoLibrary, stringResource(R.string.label_scan_choose_photo))
                    }
                    Shutter(enabled = !reading, onClick = shoot)
                    // Balances the photo button, so the shutter sits in the middle.
                    Spacer(Modifier.size(52.dp))
                }
            }
            // The shutter flash, over everything.
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
        }
    }

    (state as? LabelScanState.Failed)?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.label_scan_failed_title)) },
            text = { Text(it.message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.label_scan_try_again)) } },
        )
    }
}

private enum class Hint { Aim, Found, Reading }
