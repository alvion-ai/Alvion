package com.qualcomm.alvion.feature.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

// --- Data classes ---

@Entity(tableName = "trip")
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val duration: String,
    val alerts: List<Alert>
)

data class Alert(val id: String, val type: AlertType, val timestamp: LocalTime)

enum class AlertType { DROWSINESS, DISTRACTION }

// --- Database ---

@Database(entities = [Trip::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trip_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- DAO ---

@Dao
interface TripDao {
    @Query("SELECT * FROM trip ORDER BY date DESC, startTime DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Insert
    suspend fun insert(trip: Trip)

    @Query("DELETE FROM trip")
    suspend fun deleteAll()
}

// --- Type Converters ---

class Converters {
    @TypeConverter
    fun fromAlertsList(alerts: List<Alert>?): String? {
        return Gson().toJson(alerts)
    }

    @TypeConverter
    fun toAlertsList(json: String?): List<Alert>? {
        if (json == null) return null
        val type = object : TypeToken<List<Alert>>() {}.type
        return Gson().fromJson(json, type)
    }

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.toString()
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }

    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? {
        return time?.toString()
    }

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? {
        return value?.let { LocalTime.parse(it) }
    }
}
