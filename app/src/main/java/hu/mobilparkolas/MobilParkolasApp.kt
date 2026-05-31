package hu.mobilparkolas

import android.app.Application
import hu.mobilparkolas.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class MobilParkolasApp : Application() {

    lateinit var locator: ServiceLocator
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        locator = ServiceLocator(this)
        locator.parkingNotifier.ensureChannel()

        // osmdroid needs a user agent set before any MapView is created.
        Configuration.getInstance().userAgentValue = packageName

        // First-run default: derive SMS settings from the detected SIM carrier.
        appScope.launch {
            val detected = locator.carrierDetector.detect()?.provider
            locator.settingsRepository.initDefaultsFromCarrier(detected)
        }
    }
}
