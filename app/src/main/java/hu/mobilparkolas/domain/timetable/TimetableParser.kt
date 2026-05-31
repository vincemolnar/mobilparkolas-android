package hu.mobilparkolas.domain.timetable

import hu.mobilparkolas.domain.model.ParkingStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Parses the `timetable` field returned by the NMFR endpoint. The server computes
 * this relative to the query `time` and returns the *next applicable* chargeable
 * window, e.g. "2026-06-01: 08:00 - 20:00".
 *
 * Given the current time we can therefore decide whether parking is chargeable now
 * (and warn the user that sending the SMS during a free period only starts the
 * session at the next window).
 */
object TimetableParser {

    private val REGEX = Regex(
        """(\d{4})-(\d{2})-(\d{2})\s*:\s*(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})"""
    )

    fun parseStatus(timetable: String, now: LocalDateTime): ParkingStatus {
        val m = REGEX.find(timetable) ?: return ParkingStatus.Unknown
        return try {
            val (y, mo, d, sh, sm, eh, em) = m.destructured
            val date = LocalDate.of(y.toInt(), mo.toInt(), d.toInt())
            val start = LocalDateTime.of(date, LocalTime.of(sh.toInt(), sm.toInt()))
            val end = LocalDateTime.of(date, LocalTime.of(eh.toInt(), em.toInt()))
            when {
                now.isBefore(start) -> ParkingStatus.FreeNow(nextStartsAt = start)
                now.isAfter(end) -> ParkingStatus.FreeNow(nextStartsAt = null)
                else -> ParkingStatus.ChargeableNow(endsAt = end)
            }
        } catch (e: Exception) {
            ParkingStatus.Unknown
        }
    }
}
