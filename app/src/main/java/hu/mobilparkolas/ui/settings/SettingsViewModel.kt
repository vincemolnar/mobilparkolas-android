package hu.mobilparkolas.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import hu.mobilparkolas.data.prefs.AppSettings
import hu.mobilparkolas.data.prefs.SettingsRepository
import hu.mobilparkolas.data.telephony.CarrierDetector
import hu.mobilparkolas.data.telephony.CarrierInfo
import hu.mobilparkolas.domain.sms.SmsMode
import hu.mobilparkolas.domain.sms.SmsProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    carrierDetector: CarrierDetector,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    /** Best-effort guess of the user's carrier from the SIM (null if unavailable). */
    val carrier: CarrierInfo? = carrierDetector.detect()

    fun setMode(mode: SmsMode) {
        viewModelScope.launch { repo.setSmsMode(mode) }
    }

    fun setProvider(provider: SmsProvider) {
        viewModelScope.launch { repo.setProvider(provider) }
    }

    /** Apply the detected provider and switch to provider mode in one step. */
    fun applyDetectedProvider() {
        val p = carrier?.provider ?: return
        viewModelScope.launch {
            repo.setProvider(p)
            repo.setSmsMode(SmsMode.PROVIDER)
        }
    }

    class Factory(
        private val repo: SettingsRepository,
        private val carrierDetector: CarrierDetector,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repo, carrierDetector) as T
    }
}
