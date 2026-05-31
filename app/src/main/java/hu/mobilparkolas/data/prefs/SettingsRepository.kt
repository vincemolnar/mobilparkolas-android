package hu.mobilparkolas.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import hu.mobilparkolas.domain.sms.SmsMode
import hu.mobilparkolas.domain.sms.SmsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences: which SMS addressing mode and provider to use. */
data class AppSettings(
    val smsMode: SmsMode = SmsMode.CENTRAL,
    val provider: SmsProvider = SmsProvider.YETTEL,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SMS_MODE = stringPreferencesKey("sms_mode")
        val PROVIDER = stringPreferencesKey("provider")
        val CONFIGURED = booleanPreferencesKey("configured")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            smsMode = p[Keys.SMS_MODE]?.let { runCatching { SmsMode.valueOf(it) }.getOrNull() } ?: SmsMode.CENTRAL,
            provider = p[Keys.PROVIDER]?.let { runCatching { SmsProvider.valueOf(it) }.getOrNull() } ?: SmsProvider.YETTEL,
        )
    }

    suspend fun setSmsMode(mode: SmsMode) {
        context.dataStore.edit {
            it[Keys.SMS_MODE] = mode.name
            it[Keys.CONFIGURED] = true
        }
    }

    suspend fun setProvider(provider: SmsProvider) {
        context.dataStore.edit {
            it[Keys.PROVIDER] = provider.name
            it[Keys.CONFIGURED] = true
        }
    }

    private suspend fun isConfigured(): Boolean =
        context.dataStore.data.first()[Keys.CONFIGURED] ?: false

    /**
     * On first run, derive the default SMS settings from the detected SIM carrier:
     * a supported provider -> provider mode; otherwise (One/unknown/no SIM) -> central.
     * Does nothing if the user has already configured settings.
     */
    suspend fun initDefaultsFromCarrier(detectedProvider: SmsProvider?) {
        if (isConfigured()) return
        context.dataStore.edit {
            if (detectedProvider != null && detectedProvider.supportsStop) {
                it[Keys.SMS_MODE] = SmsMode.PROVIDER.name
                it[Keys.PROVIDER] = detectedProvider.name
            } else {
                it[Keys.SMS_MODE] = SmsMode.CENTRAL.name
            }
            it[Keys.CONFIGURED] = true
        }
    }
}
