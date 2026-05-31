package hu.mobilparkolas.domain.model

import java.time.LocalDateTime

/** A geographic coordinate (WGS84). */
data class LatLng(val lat: Double, val lng: Double)

/** Axis-aligned bounding box used as a cheap pre-filter before polygon tests. */
data class BoundingBox(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
) {
    fun contains(p: LatLng): Boolean =
        p.lat in minLat..maxLat && p.lng in minLng..maxLng

    companion object {
        fun of(polygon: List<LatLng>): BoundingBox {
            var minLat = Double.MAX_VALUE
            var minLng = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE
            for (p in polygon) {
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lng < minLng) minLng = p.lng
                if (p.lng > maxLng) maxLng = p.lng
            }
            return BoundingBox(minLat, minLng, maxLat, maxLng)
        }
    }
}

/**
 * A parking zone as returned by the NMFR endpoint, enriched with a precomputed
 * bounding box for fast hit-testing.
 */
data class ParkingZone(
    val id: String,            // "BUDAPEST-0"
    val zoneCode: String,      // "1101" — always 4 digits
    val city: String,          // "Budapest XI. kerület"
    val feeHuf: Int,           // Ft / hour
    val serviceName: String,
    val timetableRaw: String,  // e.g. "2026-06-01: 08:00 - 20:00"
    val colorHex: String,
    val polygon: List<LatLng>,
) {
    val bbox: BoundingBox by lazy { BoundingBox.of(polygon) }
}

/** A registered vehicle. */
data class Car(
    val id: Long = 0,
    val plate: String,
    val name: String,
    val isDefault: Boolean = false,
)

enum class SessionStatus { ACTIVE, STOPPED }

/** A parking session the user has started (and where the car is parked). */
data class ParkingSession(
    val id: Long = 0,
    val zoneCode: String,
    val city: String,
    val plate: String,
    val startedAt: LocalDateTime,
    val stoppedAt: LocalDateTime? = null,
    val lat: Double,
    val lng: Double,
    val status: SessionStatus = SessionStatus.ACTIVE,
    /** If set, parking is scheduled to begin then (started SMS sent during a free period). */
    val scheduledStart: LocalDateTime? = null,
) {
    /** True while the parking is only scheduled and has not begun yet. */
    fun isPending(now: LocalDateTime = LocalDateTime.now()): Boolean =
        scheduledStart != null && now.isBefore(scheduledStart)

    /** When the parking actually counts from. */
    val effectiveStart: LocalDateTime get() = scheduledStart ?: startedAt
}

/**
 * Whether parking is currently chargeable in a zone, derived from the timetable
 * the server returned relative to the query time.
 */
sealed interface ParkingStatus {
    /** Chargeable right now; the current window ends at [endsAt]. */
    data class ChargeableNow(val endsAt: LocalDateTime) : ParkingStatus

    /** Free right now; the next chargeable window starts at [nextStartsAt] (null = unknown). */
    data class FreeNow(val nextStartsAt: LocalDateTime?) : ParkingStatus

    /** Could not parse the timetable. */
    data object Unknown : ParkingStatus
}
