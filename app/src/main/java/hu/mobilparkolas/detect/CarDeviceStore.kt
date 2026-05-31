package hu.mobilparkolas.detect

import android.content.Context

/** A saved car device whose Bluetooth reconnection signals "returned to the vehicle". */
data class CarDevice(val address: String, val name: String)

/**
 * Stores the user's car Bluetooth devices and the Android Auto detection toggle.
 * Backed by SharedPreferences (simple set of "address|name" strings).
 */
class CarDeviceStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("car_devices", Context.MODE_PRIVATE)

    fun devices(): List<CarDevice> =
        (prefs.getStringSet(KEY_DEVICES, emptySet()) ?: emptySet()).mapNotNull { entry ->
            val i = entry.indexOf('|')
            if (i <= 0) null else CarDevice(entry.substring(0, i), entry.substring(i + 1))
        }.sortedBy { it.name.lowercase() }

    fun addDevice(device: CarDevice) {
        val set = current().toMutableSet()
        set.removeAll { it.startsWith(device.address + "|") }
        set.add(device.address + "|" + device.name)
        prefs.edit().putStringSet(KEY_DEVICES, set).apply()
    }

    fun removeDevice(address: String) {
        val set = current().filterNot { it.startsWith(address + "|") }.toMutableSet()
        prefs.edit().putStringSet(KEY_DEVICES, set).apply()
    }

    fun watchedAddresses(): Set<String> = devices().map { it.address }.toSet()

    var androidAutoEnabled: Boolean
        get() = prefs.getBoolean(KEY_AA, true)
        set(value) = prefs.edit().putBoolean(KEY_AA, value).apply()

    private fun current(): Set<String> = prefs.getStringSet(KEY_DEVICES, emptySet()) ?: emptySet()

    private companion object {
        const val KEY_DEVICES = "devices"
        const val KEY_AA = "android_auto_enabled"
    }
}
