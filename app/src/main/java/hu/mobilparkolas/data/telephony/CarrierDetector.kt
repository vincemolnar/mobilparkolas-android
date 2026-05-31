package hu.mobilparkolas.data.telephony

import android.content.Context
import android.telephony.TelephonyManager
import hu.mobilparkolas.domain.sms.SmsProvider

/**
 * What we could infer about the user's mobile carrier from the SIM.
 * [provider] is non-null only when the carrier has a usable provider-specific SMS
 * number (i.e. one that supports STOP). One/Vodafone, DIGI, MVNOs and unknown SIMs
 * map to null, meaning the central NMFR number should be used.
 */
data class CarrierInfo(
    val name: String,
    val provider: SmsProvider?,
)

/**
 * Reads the SIM operator (MCC+MNC) to guess the carrier. `getSimOperator()` needs no
 * runtime permission. Returns null if there is no SIM / it can't be read.
 */
class CarrierDetector(context: Context) {

    private val tm =
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun detect(): CarrierInfo? {
        val manager = tm ?: return null
        val operator = manager.simOperator?.takeIf { it.isNotBlank() } ?: return null
        val simName = manager.simOperatorName?.takeIf { it.isNotBlank() } ?: operator
        return when (operator) {
            "21601" -> CarrierInfo("Yettel", SmsProvider.YETTEL)
            "21630" -> CarrierInfo("Telekom", SmsProvider.TELEKOM)
            "21670" -> CarrierInfo("One Magyarország (Vodafone)", null) // nincs STOP SMS
            else -> CarrierInfo(simName, null)
        }
    }
}
