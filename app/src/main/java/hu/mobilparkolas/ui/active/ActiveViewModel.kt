package hu.mobilparkolas.ui.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import hu.mobilparkolas.data.prefs.SettingsRepository
import hu.mobilparkolas.data.repo.ParkingRepository
import hu.mobilparkolas.detect.ReturnDetectionController
import hu.mobilparkolas.domain.model.ParkingSession
import hu.mobilparkolas.notif.ParkingNotifier
import hu.mobilparkolas.domain.sms.SmsComposer
import hu.mobilparkolas.domain.sms.SmsPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ActiveViewModel(
    private val parkingRepo: ParkingRepository,
    private val settingsRepo: SettingsRepository,
    private val parkingNotifier: ParkingNotifier,
    private val returnDetection: ReturnDetectionController,
) : ViewModel() {

    val active = parkingRepo.activeSession

    /** Builds the STOP SMS for the active session using the saved SMS settings. */
    suspend fun prepareStop(session: ParkingSession): SmsPlan {
        val s = settingsRepo.settings.first()
        return SmsComposer.stopSms(s.smsMode, session.zoneCode, session.plate, s.provider)
    }

    fun recordStop(session: ParkingSession) {
        viewModelScope.launch {
            returnDetection.stop()
            parkingRepo.stop(session)
            parkingNotifier.cancel()
        }
    }

    /** The parking was never actually started — drop it without sending a STOP SMS. */
    fun discard(session: ParkingSession) {
        viewModelScope.launch {
            returnDetection.stop()
            parkingRepo.delete(session)
            parkingNotifier.cancel()
        }
    }

    class Factory(
        private val parkingRepo: ParkingRepository,
        private val settingsRepo: SettingsRepository,
        private val parkingNotifier: ParkingNotifier,
        private val returnDetection: ReturnDetectionController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ActiveViewModel(parkingRepo, settingsRepo, parkingNotifier, returnDetection) as T
    }
}
