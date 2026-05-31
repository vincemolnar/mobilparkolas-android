package hu.mobilparkolas.di

import android.content.Context
import hu.mobilparkolas.data.api.NetworkModule
import hu.mobilparkolas.data.db.AppDatabase
import hu.mobilparkolas.data.location.LocationProvider
import hu.mobilparkolas.data.prefs.SettingsRepository
import hu.mobilparkolas.data.telephony.CarrierDetector
import hu.mobilparkolas.data.repo.CarRepository
import hu.mobilparkolas.data.repo.ParkingRepository
import hu.mobilparkolas.data.repo.ZoneRepository
import hu.mobilparkolas.detect.CarDeviceStore
import hu.mobilparkolas.detect.ReturnDetectionController
import hu.mobilparkolas.notif.ParkingNotifier

/**
 * Minimal manual dependency container. A single instance lives on the Application;
 * swappable for Hilt later if the graph grows.
 */
class ServiceLocator(context: Context) {
    private val appContext = context.applicationContext
    private val db by lazy { AppDatabase.get(appContext) }

    val zoneRepository by lazy { ZoneRepository(NetworkModule.createZoneApi(), appContext) }
    val carRepository by lazy { CarRepository(db.carDao()) }
    val parkingRepository by lazy { ParkingRepository(db.parkingSessionDao()) }
    val settingsRepository by lazy { SettingsRepository(appContext) }
    val locationProvider by lazy { LocationProvider(appContext) }
    val carrierDetector by lazy { CarrierDetector(appContext) }
    val parkingNotifier by lazy { ParkingNotifier(appContext) }
    val carDeviceStore by lazy { CarDeviceStore(appContext) }
    val returnDetection by lazy { ReturnDetectionController(appContext) }
}
