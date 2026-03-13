package com.qualcomm.alvion.feature.home.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.qualcomm.alvion.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles contextual audio alerts with low latency.
 * Optimized for critical driver safety alerts.
 */
class AlertAudioManager(
    private val context: Context,
) {
    private var isLoaded = false
    private var soundId: Int = -1

    private val soundPool =
        SoundPool
            .Builder()
            .setMaxStreams(5)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build(),
            ).build()

    init {
        // Set listener before loading to ensure we catch the completion event
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                isLoaded = true
                Log.d("AlertAudioManager", "Sound loaded successfully")
            } else {
                Log.e("AlertAudioManager", "Failed to load sound, status: $status")
            }
        }

        try {
            soundId = soundPool.load(context, R.raw.alert_beep, 1)
        } catch (e: Exception) {
            Log.e("AlertAudioManager", "Error loading sound resource", e)
        }
    }

    fun playDrowsyAlert() {
        if (!isLoaded || soundId == -1) {
            Log.w("AlertAudioManager", "Drowsy alert skipped: Sound not loaded yet")
            return
        }
        if (!canPlay()) return

        Log.d("AlertAudioManager", "Playing Drowsy Alert sequence")
        // High Urgency: 5 rapid, clear beeps
        CoroutineScope(Dispatchers.Default).launch {
            repeat(5) {
                soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.2f)
                delay(120)
            }
        }
    }

    fun playDistractionAlert() {
        if (!isLoaded || soundId == -1) {
            Log.w("AlertAudioManager", "Distraction alert skipped: Sound not loaded yet")
            return
        }
        if (!canPlay()) return

        Log.d("AlertAudioManager", "Playing Distraction Alert")
        // Normal Urgency: Just one clear beep
        soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    private var lastAlertTime = 0L
    private val ALERT_COOLDOWN_MS = 800L // Reduced from 2000L to be more responsive

    private fun canPlay(): Boolean {
        val now = System.currentTimeMillis()
        return if (now - lastAlertTime >= ALERT_COOLDOWN_MS) {
            lastAlertTime = now
            true
        } else {
            false
        }
    }

    fun release() {
        soundPool.release()
    }
}
