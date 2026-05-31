package hu.mobilparkolas.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plate: String,
    val name: String,
    val isDefault: Boolean = false,
)

@Entity(tableName = "parking_sessions")
data class ParkingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val zoneCode: String,
    val city: String,
    val plate: String,
    val startedAtEpochSec: Long,
    val stoppedAtEpochSec: Long? = null,
    val lat: Double,
    val lng: Double,
    val active: Boolean = true,
    val scheduledStartEpochSec: Long? = null,
)
