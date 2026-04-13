package com.qualcomm.alvion.feature.home.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlin.math.roundToInt

/**
 * Returns the device's current GPS speed in km/h, rounded to the nearest integer.
 * Returns 0 when [enabled] is false, GPS is unavailable, or the device is stationary.
 *
 * Requires ACCESS_FINE_LOCATION in AndroidManifest.xml:
 *   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 *
 * Uses the Fused Location Provider for best accuracy and battery efficiency.
 * Location updates are unregistered automatically when [enabled] becomes false
 * or the composable leaves the composition.
 */
@Composable
fun rememberCurrentSpeedKmh(enabled: Boolean): Int {
    val context = LocalContext.current
    var speedKmh by remember { mutableIntStateOf(0) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    // Request permission as soon as the composable enters the composition.
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(enabled, hasPermission) {
        if (!enabled || !hasPermission) {
            speedKmh = 0
            return@DisposableEffect onDispose {}
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            /* intervalMillis = */ 1_000L,
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setMinUpdateDistanceMeters(0f) // Report even if stationary (speed = 0)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                // location.speed is in m/s; convert to km/h
                speedKmh = if (location.hasSpeed() && location.speed >= 0f) {
                    (location.speed * 3.6f).roundToInt()
                } else {
                    0
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Permission was revoked at runtime
            speedKmh = 0
        }

        onDispose {
            fusedClient.removeLocationUpdates(callback)
            speedKmh = 0
        }
    }

    return speedKmh
}
