package com.qualcomm.alvion.feature.history

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Trip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateLabel: String = "", // e.g., "Oct 24, 2023"
    val startTime: Timestamp? = null,
    val endTime: Timestamp? = null,
    val durationLabel: String = "", // e.g., "45 mins"
    val alerts: List<TripAlert> = emptyList(),
)

data class TripAlert(
    val type: String = "", // "DROWSINESS" or "DISTRACTION"
    val timestamp: Timestamp? = null,
    val count: Int = 1, // Optional: if you want to group them
)
