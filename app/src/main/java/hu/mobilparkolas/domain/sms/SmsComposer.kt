package hu.mobilparkolas.domain.sms

/** How the start/stop SMS is addressed and formatted. */
enum class SmsMode {
    /** Central NMFR numbers; body carries the zone code. */
    CENTRAL,

    /** Provider-specific number whose last 4 digits are the zone code; body is just the plate. */
    PROVIDER,
}

/**
 * SMS providers whose number is built as [numberPrefix] + 4-digit zone code.
 * [supportsStop] is false where the provider has no stop SMS (e.g. One).
 */
enum class SmsProvider(val displayName: String, val numberPrefix: String, val supportsStop: Boolean) {
    YETTEL("Yettel", "+3620763", true),
    TELEKOM("Telekom", "+3630763", true),
    ONE("One Magyarország", "+3670763", false),
}

/** A ready-to-send SMS: recipient number + message body. */
data class SmsPlan(val number: String, val body: String, val supportsStop: Boolean = true)

/**
 * Builds the start/stop SMS for both addressing modes. Pure logic, no Android deps,
 * so it is unit-testable.
 */
object SmsComposer {

    const val CENTRAL_START_NUMBER = "+36303444805"
    const val CENTRAL_STOP_NUMBER = "+36303444806"

    fun startSms(mode: SmsMode, zoneCode: String, plate: String, provider: SmsProvider?): SmsPlan =
        when (mode) {
            SmsMode.CENTRAL -> SmsPlan(
                number = CENTRAL_START_NUMBER,
                body = "$zoneCode ${normalizePlate(plate)}",
            )
            SmsMode.PROVIDER -> {
                val p = requireNotNull(provider) { "PROVIDER mode requires a provider" }
                SmsPlan(
                    number = p.numberPrefix + zoneCode,
                    body = normalizePlate(plate),
                    supportsStop = p.supportsStop,
                )
            }
        }

    fun stopSms(mode: SmsMode, zoneCode: String, plate: String, provider: SmsProvider?): SmsPlan =
        when (mode) {
            SmsMode.CENTRAL -> SmsPlan(
                number = CENTRAL_STOP_NUMBER,
                body = "STOP ${normalizePlate(plate)}",
            )
            SmsMode.PROVIDER -> {
                val p = requireNotNull(provider) { "PROVIDER mode requires a provider" }
                SmsPlan(
                    number = p.numberPrefix + zoneCode,
                    body = "STOP",
                    supportsStop = p.supportsStop,
                )
            }
        }

    /** Hungarian plates are sent without the hyphen and uppercased, e.g. "ABC-123" -> "ABC123". */
    fun normalizePlate(plate: String): String =
        plate.trim().uppercase().replace("-", "").replace(" ", "")
}
