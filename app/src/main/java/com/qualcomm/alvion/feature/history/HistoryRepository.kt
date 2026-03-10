package com.qualcomm.alvion.feature.history

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class HistoryRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val userId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun saveTrip(trip: Trip) {
        if (userId.isEmpty()) return
        try {
            val tripWithUser = trip.copy(userId = userId)
            firestore
                .collection("trips")
                .add(tripWithUser)
                .await()
        } catch (e: Exception) {
            Log.e("HistoryRepository", "Error saving trip", e)
        }
    }

    fun getTripHistory(): Flow<List<Trip>> =
        callbackFlow {
            if (userId.isEmpty()) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }

            val subscription =
                firestore
                    .collection("trips")
                    .whereEqualTo("userId", userId)
                    .orderBy("startTime", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("HistoryRepository", "Error listening to history", error)
                            trySend(emptyList()) // Return empty rather than crashing
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val trips = snapshot.toObjects(Trip::class.java)
                            trySend(trips)
                        }
                    }

            awaitClose { subscription.remove() }
        }

    suspend fun clearHistory() {
        if (userId.isEmpty()) return
        try {
            val snapshot =
                firestore
                    .collection("trips")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("HistoryRepository", "Error clearing history", e)
        }
    }
}
