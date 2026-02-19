package com.qualcomm.alvion.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val tripDao: TripDao) : ViewModel() {

    val allTrips: StateFlow<List<Trip>> = tripDao.getAllTrips()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTrip(trip: Trip) {
        viewModelScope.launch {
            tripDao.insert(trip)
        }
    }

    fun clearAllTrips() {
        viewModelScope.launch {
            tripDao.deleteAll()
        }
    }
}

class HistoryViewModelFactory(private val tripDao: TripDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(tripDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
