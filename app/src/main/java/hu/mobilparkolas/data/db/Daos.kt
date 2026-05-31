package hu.mobilparkolas.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query("SELECT * FROM cars ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<CarEntity>>

    @Query("SELECT * FROM cars WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): CarEntity?

    @Query("SELECT * FROM cars WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CarEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(car: CarEntity): Long

    @Update
    suspend fun update(car: CarEntity)

    @Delete
    suspend fun delete(car: CarEntity)

    @Query("UPDATE cars SET isDefault = 0")
    suspend fun clearDefaults()
}

@Dao
interface ParkingSessionDao {
    @Query("SELECT * FROM parking_sessions WHERE active = 1 LIMIT 1")
    fun observeActive(): Flow<ParkingSessionEntity?>

    @Query("SELECT * FROM parking_sessions WHERE active = 1 LIMIT 1")
    suspend fun getActive(): ParkingSessionEntity?

    @Query("SELECT * FROM parking_sessions ORDER BY startedAtEpochSec DESC")
    fun observeHistory(): Flow<List<ParkingSessionEntity>>

    @Insert
    suspend fun insert(session: ParkingSessionEntity): Long

    @Update
    suspend fun update(session: ParkingSessionEntity)

    @Delete
    suspend fun delete(session: ParkingSessionEntity)

    @Query("DELETE FROM parking_sessions")
    suspend fun deleteAll()
}
