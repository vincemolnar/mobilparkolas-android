package hu.mobilparkolas.data.repo

import hu.mobilparkolas.data.db.ParkingSessionDao
import hu.mobilparkolas.data.db.ParkingSessionEntity
import hu.mobilparkolas.domain.model.LatLng
import hu.mobilparkolas.domain.model.ParkingSession
import hu.mobilparkolas.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneId

class ParkingRepository(private val dao: ParkingSessionDao) {

    val activeSession: Flow<ParkingSession?> = dao.observeActive().map { it?.toDomain() }
    val history: Flow<List<ParkingSession>> = dao.observeHistory().map { list -> list.map { it.toDomain() } }

    suspend fun getActive(): ParkingSession? = dao.getActive()?.toDomain()

    /** Records that a parking session has started (call after the start SMS is sent). */
    suspend fun start(
        zoneCode: String,
        city: String,
        plate: String,
        where: LatLng,
        scheduledStart: LocalDateTime? = null,
    ): Long {
        val now = LocalDateTime.now()
        return dao.insert(
            ParkingSessionEntity(
                zoneCode = zoneCode,
                city = city,
                plate = plate,
                startedAtEpochSec = now.toEpochSec(),
                lat = where.lat,
                lng = where.lng,
                active = true,
                scheduledStartEpochSec = scheduledStart?.toEpochSec(),
            )
        )
    }

    /** Marks the active session stopped (call after the STOP SMS is sent). */
    suspend fun stop(session: ParkingSession) {
        dao.update(
            session.toEntity().copy(
                active = false,
                stoppedAtEpochSec = LocalDateTime.now().toEpochSec(),
            )
        )
    }

    suspend fun delete(session: ParkingSession) = dao.delete(session.toEntity())

    suspend fun deleteAll() = dao.deleteAll()

    private fun LocalDateTime.toEpochSec(): Long =
        atZone(ZoneId.systemDefault()).toEpochSecond()

    private fun Long.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(this), ZoneId.systemDefault())

    private fun ParkingSessionEntity.toDomain() = ParkingSession(
        id = id,
        zoneCode = zoneCode,
        city = city,
        plate = plate,
        startedAt = startedAtEpochSec.toLocalDateTime(),
        stoppedAt = stoppedAtEpochSec?.toLocalDateTime(),
        lat = lat,
        lng = lng,
        status = if (active) SessionStatus.ACTIVE else SessionStatus.STOPPED,
        scheduledStart = scheduledStartEpochSec?.toLocalDateTime(),
    )

    private fun ParkingSession.toEntity() = ParkingSessionEntity(
        id = id,
        zoneCode = zoneCode,
        city = city,
        plate = plate,
        startedAtEpochSec = startedAt.toEpochSec(),
        stoppedAtEpochSec = stoppedAt?.toEpochSec(),
        lat = lat,
        lng = lng,
        active = status == SessionStatus.ACTIVE,
        scheduledStartEpochSec = scheduledStart?.toEpochSec(),
    )
}
