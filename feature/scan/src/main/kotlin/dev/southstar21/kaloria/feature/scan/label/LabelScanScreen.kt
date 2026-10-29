package dev.southstar21.kaloria.feature.scan.label

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.LocalGlassOverCamera
import androidx.compose.runtime.CompositionLocalProvider
import dev.southstar21.kaloria.core.scanner.label.NanoStatus
import dev.southstar21.kaloria.feature.scan.LabelScanKey
import dev.southstar21.kaloria.feature.scan.camera.CameraPreview

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
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
        viewModel.read(bitmap)
    }

    LabelScanScreen(
        viewModel = viewModel,
        capture = capture,
        onBack = onBack,
        onShutter = {
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = image.toUprightBitmap()
                        image.close()
                        viewModel.read(bitmap)
                    }

                    override fun onError(exception: ImageCaptureException) = Unit
                },
            )
        },
        onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

/**
 * Longest side of the image handed to OCR and Gemini Nano. Plenty for a label's small print, and
 * a 12 MP frame would cost seconds and ~50 MB for nothing.
 */
private const val MAX_SIDE = 2048

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
    onBack: () -> Unit,
    onShutter: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    val state = viewModel.state
    // Nothing can be captured behind the live camera, so controls use tinted, non-refracting glass.
    CompositionLocalProvider(LocalGlassOverCamera provides true) {
    Box(Modifier.fillMaxSize()) {
        CameraPreview(useCases = remember(capture) { listOf(capture) })

        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 300.dp, height = 420.dp)
                .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(28.dp)),
        )

        Column(Modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.glass()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                NanoChip(viewModel.nano, viewModel::downloadModel)
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state == LabelScanState.Reading) {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LoadingIndicator()
                        Text(
                            if (viewModel.nano == NanoStatus.Available && !viewModel.rulesOnly) "Reading with Gemini Nano…" else "Reading the text…",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            } else {
                GlassCard(contentPadding = 14.dp) {
                    Text(
                        "Fit the whole nutrition table in the frame, flat and in focus.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                IconButton(onClick = onPickPhoto, modifier = Modifier.glass(CircleShape)) {
                    Icon(Icons.Rounded.PhotoLibrary, "Choose a photo")
                }
                FilledIconButton(
                    onClick = onShutter,
                    enabled = state != LabelScanState.Reading,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White),
                ) {}
                FilterChip(
                    selected = viewModel.rulesOnly,
                    onClick = { viewModel.rulesOnly = !viewModel.rulesOnly },
                    label = { Text("Rules") },
                    leadingIcon = { Icon(Icons.Rounded.TextFields, null) },
                    modifier = Modifier.glass(CircleShape),
                )
            }
        }
    }
    }

    (state as? LabelScanState.Failed)?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Couldn't read the label") },
            text = { Text(it.message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("Try again") } },
        )
    }
}

@Composable
private fun NanoChip(status: NanoStatus?, onDownload: () -> Unit) {
    val (text, action) = when (status) {
        NanoStatus.Available -> "On-device AI ready" to null
        NanoStatus.Downloadable -> "Download on-device AI" to onDownload
        NanoStatus.Downloading -> "Downloading AI model…" to null
        NanoStatus.Unavailable -> "AI unavailable, using text rules" to null
        null -> "Checking on-device AI…" to null
    }
    Row(
        Modifier.glass(CircleShape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
        if (action != null) {
            TextButton(onClick = action) { Text(text) }
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
