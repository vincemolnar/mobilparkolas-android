package hu.mobilparkolas

import hu.mobilparkolas.domain.model.ParkingStatus
import hu.mobilparkolas.domain.timetable.TimetableParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TimetableParserTest {

    private val tt = "2026-06-01: 08:00 - 20:00"

    @Test
    fun chargeableWhenInsideWindow() {
        val status = TimetableParser.parseStatus(tt, LocalDateTime.of(2026, 6, 1, 10, 0))
        assertTrue(status is ParkingStatus.ChargeableNow)
        assertEquals(LocalDateTime.of(2026, 6, 1, 20, 0), (status as ParkingStatus.ChargeableNow).endsAt)
    }

    @Test
    fun freeBeforeWindowReturnsNextStart() {
        val status = TimetableParser.parseStatus(tt, LocalDateTime.of(2026, 5, 31, 10, 3))
        assertTrue(status is ParkingStatus.FreeNow)
        assertEquals(LocalDateTime.of(2026, 6, 1, 8, 0), (status as ParkingStatus.FreeNow).nextStartsAt)
    }

    @Test
    fun unparseableReturnsUnknown() {
        assertEquals(ParkingStatus.Unknown, TimetableParser.parseStatus("nincs adat", LocalDateTime.now()))
    }
}
