package hu.mobilparkolas.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import hu.mobilparkolas.data.prefs.AppSettings
import hu.mobilparkolas.di.ServiceLocator
import hu.mobilparkolas.domain.geo.Geometry
import hu.mobilparkolas.domain.model.Car
import hu.mobilparkolas.domain.model.LatLng
import hu.mobilparkolas.domain.model.ParkingStatus
import hu.mobilparkolas.domain.model.ParkingZone
import hu.mobilparkolas.domain.sms.SmsComposer
import hu.mobilparkolas.domain.sms.SmsPlan
import hu.mobilparkolas.domain.timetable.TimetableParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Zones drawn within this radius (m) of the location, to keep map overlays light. */
private const val DRAW_RADIUS_M = 5_000.0

/** What the bottom sheet shows. */
sealed interface SheetState {
    data object Locating : SheetState
    data object NeedPermission : SheetState
    data class NoZone(val nearby: List<ParkingZone>) : SheetState
    data class Zone(val zone: ParkingZone, val status: ParkingStatus) : SheetState
    data class Error(val message: String) : SheetState
}

/** Everything needed to send the start SMS. */
data class PreparedStart(
    val zone: ParkingZone,
    val car: Car,
    val plan: SmsPlan,
    val where: LatLng,
    /** If set, parking is scheduled for this (next chargeable) time. */
    val scheduledStart: LocalDateTime? = null,
)

sealed interface QuickParkOutcome {
    data class Ready(val start: PreparedStart) : QuickParkOutcome
    data object NoDefaultCar : QuickParkOutcome
    data class Failed(val message: String) : QuickParkOutcome
}

class MainViewModel(private val locator: ServiceLocator) : ViewModel() {

    private val zoneRepository = locator.zoneRepository
    private val locationProvider = locator.locationProvider
    private val carRepository = locator.carRepository
    private val settingsRepository = locator.settingsRepository
    private val parkingRepository = locator.parkingRepository
    private val parkingNotifier = locator.parkingNotifier

    /** Drives the splash screen: stays false until the initial zone sync finishes. */
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _sheet = MutableStateFlow<SheetState>(SheetState.Locating)
    val sheet: StateFlow<SheetState> = _sheet.asStateFlow()

    private val _center = MutableStateFlow<LatLng?>(null)
    val center: StateFlow<LatLng?> = _center.asStateFlow()

    private val _zones = MutableStateFlow<List<ParkingZone>>(emptyList())
    val zones: StateFlow<List<ParkingZone>> = _zones.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    val cars: StateFlow<List<Car>> =
        carRepository.cars.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    /** One-shot event: the widget asked for an immediate quick-park; the UI launches the SMS. */
    private val _autoStart = MutableSharedFlow<PreparedStart>(extraBufferCapacity = 1)
    val autoStart: SharedFlow<PreparedStart> = _autoStart.asSharedFlow()

    private var hasPermission = false
    private var autoQuickParkPending = false
    private var autoQuickParkCarId: Long? = null

    /** The initial zone-database sync; detection waits on it so it never runs on empty data. */
    private val syncJob: Job = viewModelScope.launch {
        try {
            zoneRepository.ensureLoaded(LocalDateTime.now())
        } catch (e: Exception) {
            _sheet.value = SheetState.Error(e.message ?: "Nem sikerült a zónák letöltése")
        } finally {
            _isReady.value = true // dismiss splash once the DB is ready
        }
    }

    fun onPermissionResult(granted: Boolean) {
        hasPermission = granted
        if (granted) locate() else _sheet.value = SheetState.NeedPermission
    }

    fun locate() {
        viewModelScope.launch {
            _sheet.value = SheetState.Locating
            syncJob.join() // make sure the zone DB is loaded before detecting
            val point = runCatching { locationProvider.current() }.getOrNull()
            if (point == null) {
                _sheet.value = SheetState.Error("Nem sikerült meghatározni a helyzetet.")
                return@launch
            }
            _center.value = point
            _zones.value = zoneRepository.allZones.filter {
                Geometry.haversineMeters(point, Geometry.centroid(it.polygon)) < DRAW_RADIUS_M
            }
            val hits = zoneRepository.zonesAt(point)
            if (hits.isNotEmpty()) {
                val detected = hits.first()
                // Highlight the detected polygon on the map (id must match the drawn zones).
                _selectedId.value = detected.id
                val fresh = refreshTimetable(detected)
                val status = statusOf(fresh)
                _sheet.value = SheetState.Zone(fresh, status)
                maybeAutoQuickPark(fresh, status)
            } else {
                _sheet.value = SheetState.NoZone(zoneRepository.nearbyZones(point))
                autoQuickParkPending = false
            }
        }
    }

    /** Called when launched from the widget: after detection, auto-launch the start SMS. */
    fun requestAutoQuickPark(carId: Long?) {
        autoQuickParkPending = true
        autoQuickParkCarId = carId
    }

    private suspend fun maybeAutoQuickPark(zone: ParkingZone, status: ParkingStatus) {
        if (!autoQuickParkPending) return
        autoQuickParkPending = false
        if (status is ParkingStatus.FreeNow) return // free now: no ticket needed
        val outcome = prepareQuickParkFor(zone, autoQuickParkCarId)
        if (outcome is QuickParkOutcome.Ready) _autoStart.emit(outcome.start)
    }

    /** Select a zone (from a map tap or detection); refreshes its timetable for freshness. */
    fun select(zone: ParkingZone) {
        viewModelScope.launch {
            _selectedId.value = zone.id
            val fresh = refreshTimetable(zone)
            _sheet.value = SheetState.Zone(fresh, statusOf(fresh))
        }
    }

    fun selectById(id: String) {
        zoneRepository.allZones.firstOrNull { it.id == id }?.let { select(it) }
    }

    suspend fun prepareQuickPark(zone: ParkingZone): QuickParkOutcome = prepareQuickParkFor(zone, null)

    /** Build the start SMS for [zone] using [carId] if given, otherwise the default car. */
    private suspend fun prepareQuickParkFor(zone: ParkingZone, carId: Long?): QuickParkOutcome {
        return try {
            val car = (carId?.let { carRepository.getById(it) }) ?: carRepository.getDefault()
                ?: return QuickParkOutcome.NoDefaultCar
            val settings = settingsRepository.settings.first()
            val plan = SmsComposer.startSms(settings.smsMode, zone.zoneCode, car.plate, settings.provider)
            val where = _center.value ?: Geometry.centroid(zone.polygon)
            QuickParkOutcome.Ready(PreparedStart(zone, car, plan, where))
        } catch (e: Exception) {
            QuickParkOutcome.Failed(e.message ?: "Ismeretlen hiba")
        }
    }

    fun recordStart(start: PreparedStart) {
        viewModelScope.launch {
            parkingRepository.start(
                start.zone.zoneCode, start.zone.city, start.car.plate, start.where, start.scheduledStart,
            )
            parkingRepository.getActive()?.let { parkingNotifier.showOngoing(it) }
            locator.returnDetection.start()
        }
    }

    private suspend fun refreshTimetable(zone: ParkingZone): ParkingZone {
        if (zone.city.isBlank()) return zone
        return runCatching {
            zoneRepository.refreshCity(zone.city, LocalDateTime.now())
                .firstOrNull { it.zoneCode == zone.zoneCode } ?: zone
        }.getOrDefault(zone)
    }

    private fun statusOf(zone: ParkingZone): ParkingStatus =
        TimetableParser.parseStatus(zone.timetableRaw, LocalDateTime.now())

    class Factory(private val locator: ServiceLocator) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(locator) as T
    }
}
