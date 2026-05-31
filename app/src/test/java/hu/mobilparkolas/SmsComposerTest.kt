package hu.mobilparkolas

import hu.mobilparkolas.domain.sms.SmsComposer
import hu.mobilparkolas.domain.sms.SmsMode
import hu.mobilparkolas.domain.sms.SmsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SmsComposerTest {

    @Test
    fun centralStartCarriesZoneAndPlate() {
        val plan = SmsComposer.startSms(SmsMode.CENTRAL, "1101", "abc-123", null)
        assertEquals(SmsComposer.CENTRAL_START_NUMBER, plan.number)
        assertEquals("1101 ABC123", plan.body)
    }

    @Test
    fun centralStopUsesStopKeyword() {
        val plan = SmsComposer.stopSms(SmsMode.CENTRAL, "1101", "abc-123", null)
        assertEquals(SmsComposer.CENTRAL_STOP_NUMBER, plan.number)
        assertEquals("STOP ABC123", plan.body)
    }

    @Test
    fun providerEmbedsZoneInNumberAndSendsOnlyPlate() {
        val plan = SmsComposer.startSms(SmsMode.PROVIDER, "1101", "ABC123", SmsProvider.YETTEL)
        assertEquals("+36207631101", plan.number)
        assertEquals("ABC123", plan.body)
    }

    @Test
    fun oneProviderHasNoStop() {
        val plan = SmsComposer.startSms(SmsMode.PROVIDER, "1101", "ABC123", SmsProvider.ONE)
        assertFalse(plan.supportsStop)
    }
}
