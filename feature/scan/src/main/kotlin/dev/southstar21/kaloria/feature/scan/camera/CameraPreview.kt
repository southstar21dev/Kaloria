package dev.southstar21.kaloria.feature.scan.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.OrbState
import dev.southstar21.kaloria.core.designsystem.component.ThinkingOrb
import dev.southstar21.kaloria.feature.scan.R
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Back camera preview with extra CameraX [useCases] (analysis, capture) bound to the screen's
 * lifecycle. Asks for the camera permission first.
 */
@Composable
fun CameraPreview(
    useCases: List<UseCase>,
    modifier: Modifier = Modifier,
    onCamera: (Camera) -> Unit = {},
) {
    val context = LocalContext.current
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasPermission()) }
    // Saved, so turning the phone doesn't ask again straight away.
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted && !asked) launcher.launch(Manifest.permission.CAMERA) }
    // Picks up access granted (or revoked) in the system settings while the app was in the background.
    LifecycleResumeEffect(Unit) {
        granted = hasPermission()
        onPauseOrDispose {}
    }

    if (!granted) {
        // Denied for good ("don't ask again", or twice): the system won't show the prompt any more,
        // so the only way back is the app's settings page.
        val activity = context.findActivity()
        val blocked = asked && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        PermissionCard(
            blocked = blocked,
            asked = asked,
            onAllow = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
            modifier = modifier,
        )
        return
    }
    CameraFeed(useCases, modifier, onCamera)
}

/** The preview itself, once the camera may be used. */
@Composable
private fun CameraFeed(useCases: List<UseCase>, modifier: Modifier, onCamera: (Camera) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val preview = remember {
        Preview.Builder()
            // 16:9 at 1080p: phones are ~20:9, so a 4:3 frame (CameraX's default) gets cropped and
            // blown up ~1.6x to fill the screen, which looks soft. 16:9 needs only ~1.2x.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
            .apply { setSurfaceProvider { surfaceRequest = it } }
    }

    // Bound once the view's size is known: the viewport makes every use case (preview, analysis,
    // capture) crop to exactly what this view shows, so an analysed frame's crop rect is the screen.
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val rotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0
    val currentOnCamera by rememberUpdatedState(onCamera)
    // Only this preview's own use cases are ever unbound: while one scan screen hands over to
    // another both are composed, and unbinding everything would take the camera from the new one.
    val bound = remember { mutableListOf<UseCase>() }
    var unavailable by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycleOwner, useCases, viewSize, rotation) {
        if (viewSize == IntSize.Zero) return@LaunchedEffect
        unavailable = false
        try {
            val cameras = ProcessCameraProvider.awaitInstance(context).also { provider = it }
            val viewPort = ViewPort.Builder(Rational(viewSize.width, viewSize.height), rotation)
                .setScaleType(ViewPort.FILL_CENTER)
                .build()
            val group = UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(preview).apply { useCases.forEach(::addUseCase) }.build()
            cameras.unbind(*bound.toTypedArray())
            bound.clear()
            val cam = cameras.bindGroup(lifecycleOwner, enhancedBackCamera(context, cameras, useCases), group)
            bound += group.useCases
            camera = cam
            currentOnCamera(cam)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No back camera, or it's disabled by policy or held by another app.
            unavailable = true
        }
    }
    // The provider it was bound with, not a blocking get() of a new one on the way out.
    DisposableEffect(lifecycleOwner) {
        onDispose { runCatching { provider?.unbind(*bound.toTypedArray()) } }
    }

    // Whether the camera is open and streaming, to hand over from the orb to the picture.
    var live by remember { mutableStateOf(false) }
    DisposableEffect(camera, lifecycleOwner) {
        val state = camera?.cameraInfo?.cameraState
        val observer = Observer<CameraState> { live = it.type == CameraState.Type.OPEN }
        state?.observe(lifecycleOwner, observer)
        onDispose { state?.removeObserver(observer) }
    }

    val transformer = remember { MutableCoordinateTransformer() }
    Box(modifier.fillMaxSize().background(Color.Black).onSizeChanged { viewSize = it }) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                // Drawn like the rest of the UI rather than as a separate surface, so the preview
                // slides and fades with the screen when it opens and closes instead of lagging behind.
                implementationMode = ImplementationMode.EMBEDDED,
                coordinateTransformer = transformer,
                modifier = Modifier.fillMaxSize().pointerInput(request) {
                    // Tap to focus, for labels and barcodes the continuous autofocus misses.
                    detectTapGestures { tap ->
                        val surface = with(transformer) { tap.transform() }
                        val point = SurfaceOrientedMeteringPointFactory(request.resolution.width.toFloat(), request.resolution.height.toFloat())
                            .createPoint(surface.x, surface.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    }
                },
            )
        }
        // While the camera wakes up: a searching orb, fading into the picture once it streams.
        if (unavailable) {
            GlassCard(Modifier.align(Alignment.Center).padding(24.dp)) {
                Text(stringResource(R.string.camera_unavailable), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
        AnimatedVisibility(
            visible = !live && !unavailable,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(450, delayMillis = 150)) + scaleOut(tween(600, delayMillis = 150), targetScale = 1.15f),
        ) {
            ThinkingOrb(OrbState.Searching, size = 120.dp, dark = true)
        }
    }
}

/**
 * The back camera with the phone maker's own image processing (CameraX Extensions, "Auto" mode:
 * HDR or night processing as the scene needs) where the device offers it, as Samsung Galaxy phones
 * do. That's the difference between a flat sensor feed and what the built-in camera app shows.
 */
private suspend fun enhancedBackCamera(context: Context, provider: ProcessCameraProvider, useCases: List<UseCase>): CameraSelector {
    val back = CameraSelector.DEFAULT_BACK_CAMERA
    val extensions = runCatching { ExtensionsManager.getInstanceAsync(context, provider).await() }.getOrNull() ?: return back
    if (!extensions.isExtensionAvailable(back, ExtensionMode.AUTO)) return back
    if (useCases.any { it is ImageAnalysis } && !extensions.isImageAnalysisSupported(back, ExtensionMode.AUTO)) return back
    return extensions.getExtensionEnabledCameraSelector(back, ExtensionMode.AUTO)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    addListener({ runCatching { get() }.fold(cont::resume, cont::resumeWithException) }, Runnable::run)
    cont.invokeOnCancellation { cancel(false) }
}

@Composable
private fun PermissionCard(blocked: Boolean, asked: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.camera_access_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (blocked) R.string.camera_access_denied_body else R.string.camera_access_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (blocked) {
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.camera_access_settings)) }
                } else if (asked) {
                    Button(onClick = onAllow) { Text(stringResource(R.string.camera_access_allow)) }
                }
            }
        }
    }
}

/** Binds [group], falling back to the plain back camera, and last to taking the camera over. */
private fun ProcessCameraProvider.bindGroup(owner: LifecycleOwner, selector: CameraSelector, group: UseCaseGroup): Camera = runCatching { bindToLifecycle(owner, selector, group) }
    // A vendor extension that can't take this combination of use cases: plain camera.
    .recoverCatching { bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group) }
    // The screen being left still holds the camera mid-transition: the new one takes it.
    .recoverCatching {
        unbindAll()
        bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
    }
    .getOrThrow()
