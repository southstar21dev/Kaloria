package dev.southstar21.kaloria.core.designsystem.liquid

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Where the light comes from, in degrees, for the glass's specular rim. Tilting the phone moves
 * it, the way Liquid Glass highlights "respond to device motion". Read it lazily (from a draw
 * lambda) so only the highlights redraw.
 */
class LightSource internal constructor(initial: Float) {
    var angle by mutableFloatStateOf(initial)
        internal set
}

val LocalLightSource = staticCompositionLocalOf { LightSource(45f) }

/** A light source tied to the accelerometer while the screen is resumed (no sensor in previews/tests). */
@Composable
fun rememberLightSource(): LightSource {
    val light = remember { LightSource(45f) }
    if (LocalInspectionMode.current) return light
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var smoothed = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val target = atan2(event.values[1], event.values[0]) * (180f / PI).toFloat()
                // Low-pass so the highlight glides instead of jittering, and only publish a new
                // angle once it has moved a few degrees: every change redraws every glass element,
                // so a phone lying still must cause no redraws at all.
                // The first reading is taken as-is, so the highlights don't sweep into place after launch.
                if (smoothed.isNaN()) {
                    smoothed = target
                    light.angle = target
                    return
                }
                smoothed += (target - smoothed) * 0.2f
                if (kotlin.math.abs(smoothed - light.angle) >= MIN_STEP_DEGREES) light.angle = smoothed
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> accelerometer?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
                Lifecycle.Event.ON_PAUSE -> manager.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }
    return light
}

private const val MIN_STEP_DEGREES = 4f
