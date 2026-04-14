package com.qualcomm.alvion.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(
    private val context: Context,
) {
    private object PreferencesKeys {
        val ALERT_SOUND = booleanPreferencesKey("alert_sound")
        val VIBRATION = booleanPreferencesKey("vibration")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val PROFILE_IMAGE_URI = stringPreferencesKey("profile_image_uri")
    }

    val alertSoundFlow: Flow<Boolean> =
        context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[PreferencesKeys.ALERT_SOUND] ?: true
            }

    val vibrationFlow: Flow<Boolean> =
        context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[PreferencesKeys.VIBRATION] ?: true
            }

    val darkModeFlow: Flow<Boolean> =
        context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[PreferencesKeys.DARK_MODE] ?: false
            }

    val profileImageUriFlow: Flow<String> =
        context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[PreferencesKeys.PROFILE_IMAGE_URI] ?: ""
            }

    suspend fun updateAlertSound(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALERT_SOUND] = enabled
        }
    }

    suspend fun updateVibration(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIBRATION] = enabled
        }
    }

    suspend fun updateDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE] = enabled
        }
    }

    suspend fun updateProfileImageUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROFILE_IMAGE_URI] = uri
        }
    }
}
