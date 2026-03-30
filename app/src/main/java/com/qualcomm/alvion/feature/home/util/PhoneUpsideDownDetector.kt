package com.qualcomm.alvion.feature.home.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Detects when the device is held **upside down** relative to normal portrait use.
 *
 * - Primary: [Surface.ROTATION_180] from the default display (works when auto-rotate allows 180°).
 * - Fallback: low-pass filtered accelerometer gravity — when rotation stays at 0 but the phone is
 *   physically inverted in portrait, gravity along +Y flips sign.
 */
object PhoneUpsideDownDetector {
    fun displayRotation(context: Context): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }

    /**
     * @param gravity low-pass gravity estimate from accelerometer (m/s²), device coordinates.
     * @param configurationOrientation [android.content.res.Configuration.orientation]
     */
    fun isUpsideDown(
        displayRotation: Int,
        gravity: FloatArray,
        configurationOrientation: Int,
    ): Boolean {
        if (displayRotation == Surface.ROTATION_180) return true

        // Rotation locked to portrait: compare gravity when device is roughly vertical (in-car mount).
        if (configurationOrientation != android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            return false
        }

        val gx = gravity[0]
        val gy = gravity[1]
        val gz = gravity[2]

        // Portrait Orientation: Y axis is vertical.
        // gx should be small, gy should be roughly +/- 9.8.
        val mostlyVerticalPortrait = abs(gy) >= abs(gx) * 1.2f && abs(gy) >= abs(gz) * 0.85f
        if (!mostlyVerticalPortrait) return false

        // Typical upright portrait: gravity ≈ +9.8 along +Y. Inverted: ≈ -9.8.
        return gy < -5.5f
    }
}

/**
 * While [enabled] is true, reports whether the phone appears upside down.
 * Registers a single accelerometer listener for gravity estimation.
 */
@Composable
fun rememberPhoneUpsideDown(enabled: Boolean): Boolean {
    val context = LocalContext.current
    var upsideDown by remember { mutableStateOf(false) }
    val sensorManager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val hasAccelerometer = remember(sensorManager) { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null }

    DisposableEffect(enabled, hasAccelerometer) {
        if (!enabled || !hasAccelerometer) {
            upsideDown = false
            return@DisposableEffect onDispose { }
        }

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        val gravity = FloatArray(3)

        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
                    // Low-pass filter to approximate gravity (m/s²), device coordinates.
                    val alpha = 0.85f
                    gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                    gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                    gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                    
                    val rotation = PhoneUpsideDownDetector.displayRotation(context)
                    val configOrientation = context.resources.configuration.orientation
                    upsideDown =
                        PhoneUpsideDownDetector.isUpsideDown(
                            displayRotation = rotation,
                            gravity = gravity,
                            configurationOrientation = configOrientation,
                        )
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) {}
            }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // No accelerometer fallback: detect OS-reported 180° rotation via polling.
    if (!hasAccelerometer) {
        LaunchedEffect(enabled) {
            if (!enabled) {
                upsideDown = false
                return@LaunchedEffect
            }
            while (true) {
                upsideDown = PhoneUpsideDownDetector.displayRotation(context) == Surface.ROTATION_180
                delay(1000)
            }
        }
    }

    return upsideDown
}
