package com.qualcomm.alvion.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {
    private val repository = HistoryRepository()

    // Using Eagerly so data stays in memory even when tab is not visible
    val tripHistory: StateFlow<List<Trip>> =
        repository
            .getTripHistory()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    fun saveTrip(trip: Trip) {
        viewModelScope.launch {
            repository.saveTrip(trip)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
