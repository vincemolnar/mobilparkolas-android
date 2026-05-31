package hu.mobilparkolas.detect

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.car.app.connection.CarConnection
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import hu.mobilparkolas.MobilParkolasApp
import hu.mobilparkolas.domain.sms.SmsComposer
import hu.mobilparkolas.notif.ParkingNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDateTime

/**
 * Runs while a parking session is active. Detects the user returning to the vehicle:
 *  - a watched car Bluetooth device reconnects, or
 *  - Android Auto projection connects more than 15 minutes after the parking started
 *    (unless Bluetooth already fired).
 * On detection it posts a notification offering to stop the parking.
 */
class ReturnDetectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fired = false
    private var startedAt: LocalDateTime? = null
    private var watched: Set<String> = emptySet()
    private var carConnection: CarConnection? = null

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            val address = device?.address ?: return
            if (address in watched) fire()
        }
    }

    private val aaObserver = Observer<Int> { type ->
        if (type == CarConnection.CONNECTION_TYPE_PROJECTION) {
            val start = startedAt ?: return@Observer
            if (Duration.between(start, LocalDateTime.now()).toMinutes() >= GRACE_MINUTES) fire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val locator = (application as MobilParkolasApp).locator
        val session = runBlocking { locator.parkingRepository.getActive() }
        if (session == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startedAt = session.startedAt
        watched = locator.carDeviceStore.watchedAddresses()

        val settings = runBlocking { locator.settingsRepository.settings.first() }
        val stopPlan = SmsComposer.stopSms(settings.smsMode, session.zoneCode, session.plate, settings.provider)
        ServiceCompat.startForeground(
            this,
            ParkingNotifier.NOTIF_ID,
            locator.parkingNotifier.buildOngoingNotification(session, stopPlan),
            if (android.os.Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )

        ContextCompat.registerReceiver(
            this, btReceiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (locator.carDeviceStore.androidAutoEnabled) {
            carConnection = CarConnection(this).also { it.type.observeForever(aaObserver) }
        }
        return START_STICKY
    }

    private fun fire() {
        if (fired) return
        fired = true
        val locator = (application as MobilParkolasApp).locator
        scope.launch {
            val session = locator.parkingRepository.getActive() ?: return@launch
            val settings = locator.settingsRepository.settings.first()
            val plan = SmsComposer.stopSms(settings.smsMode, session.zoneCode, session.plate, settings.provider)
            locator.parkingNotifier.showReturnNotification(plan, session)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(btReceiver) }
        carConnection?.type?.removeObserver(aaObserver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val GRACE_MINUTES = 15L
    }
}
