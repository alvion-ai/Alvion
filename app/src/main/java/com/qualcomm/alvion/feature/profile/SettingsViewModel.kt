package com.qualcomm.alvion.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.qualcomm.alvion.core.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    val alertSoundEnabled: StateFlow<Boolean> =
        repository.alertSoundFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val vibrationEnabled: StateFlow<Boolean> =
        repository.vibrationFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val darkModeEnabled: StateFlow<Boolean> =
        repository.darkModeFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val profileImageUri: StateFlow<String> =
        repository.profileImageUriFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _displayName = MutableStateFlow(FirebaseAuth.getInstance().currentUser?.displayName ?: "")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _email = MutableStateFlow(FirebaseAuth.getInstance().currentUser?.email ?: "")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    fun updateDisplayName(newName: String) {
        _displayName.value = newName
    }

    fun saveProfileName() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            viewModelScope.launch {
                _updateStatus.value = UpdateStatus.Loading
                try {
                    val profileUpdates =
                        userProfileChangeRequest {
                            displayName = _displayName.value
                        }
                    user.updateProfile(profileUpdates).await()
                    _updateStatus.value = UpdateStatus.Success
                } catch (e: Exception) {
                    _updateStatus.value = UpdateStatus.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun toggleAlertSound(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAlertSound(enabled)
        }
    }

    fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateVibration(enabled)
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDarkMode(enabled)
        }
    }

    fun updateProfileImageUri(uri: String) {
        viewModelScope.launch {
            repository.updateProfileImageUri(uri)
        }
    }

    fun clearUpdateStatus() {
        _updateStatus.value = UpdateStatus.Idle
    }

    sealed class UpdateStatus {
        object Idle : UpdateStatus()

        object Loading : UpdateStatus()

        object Success : UpdateStatus()

        data class Error(
            val message: String,
        ) : UpdateStatus()
    }
}
