package hu.mobilparkolas.data.repo

import android.content.Context
import hu.mobilparkolas.data.api.NetworkModule
import hu.mobilparkolas.data.api.ZoneApi
import hu.mobilparkolas.data.api.ZoneDto
import hu.mobilparkolas.data.api.toDomainOrNull
import hu.mobilparkolas.domain.geo.Geometry
import hu.mobilparkolas.domain.model.LatLng
import hu.mobilparkolas.domain.model.ParkingZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Fetches and caches parking zones. The full nationwide set (~1.3 MB) is downloaded
 * at most **once per calendar day** and persisted to disk, so app restarts on the same
 * day reuse the cache instead of re-downloading. After the user picks a zone we re-query
 * that zone's city so the timetable reflects the moment of starting.
 */
class ZoneRepository(
    private val api: ZoneApi,
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cacheFile = File(context.filesDir, "zones_cache.json")
    private val prefs = context.getSharedPreferences("zone_cache", Context.MODE_PRIVATE)

    @Volatile
    var allZones: List<ParkingZone> = emptyList()
        private set

    @Volatile
    var lastSyncedAt: LocalDateTime? = null
        private set

    /**
     * Ensures zones are loaded for [now]'s day: uses the in-memory set, then the on-disk
     * cache, and only downloads if neither is fresh. Returns true if zones are available.
     */
    suspend fun ensureLoaded(now: LocalDateTime = LocalDateTime.now()): Boolean = withContext(Dispatchers.IO) {
        if (allZones.isNotEmpty() && lastSyncedAt?.toLocalDate() == now.toLocalDate()) return@withContext true

        val cachedDay = prefs.getLong(KEY_DAY, Long.MIN_VALUE)
        if (cachedDay == now.toLocalDate().toEpochDay() && cacheFile.exists()) {
            runCatching {
                val dtos = json.decodeFromString<List<ZoneDto>>(cacheFile.readText())
                allZones = dtos.mapNotNull { it.toDomainOrNull() }
                lastSyncedAt = now
            }
            if (allZones.isNotEmpty()) return@withContext true
        }
        refreshAll(now)
        allZones.isNotEmpty()
    }

    /** Forces a network download and refreshes the on-disk cache. */
    suspend fun refreshAll(now: LocalDateTime = LocalDateTime.now()): List<ParkingZone> =
        withContext(Dispatchers.IO) {
            val dtos = api.searchZones("", now.format(NetworkModule.TIME_FORMAT))
            runCatching {
                cacheFile.writeText(json.encodeToString(dtos))
                prefs.edit().putLong(KEY_DAY, now.toLocalDate().toEpochDay()).apply()
            }
            val zones = dtos.mapNotNull { it.toDomainOrNull() }
            allZones = zones
            lastSyncedAt = now
            zones
        }

    /** Re-query a single city for fresh timetable/fee data. */
    suspend fun refreshCity(city: String, now: LocalDateTime = LocalDateTime.now()): List<ParkingZone> =
        withContext(Dispatchers.IO) {
            api.searchZones(city, now.format(NetworkModule.TIME_FORMAT)).mapNotNull { it.toDomainOrNull() }
        }

    fun zonesAt(point: LatLng): List<ParkingZone> = Geometry.zonesContaining(point, allZones)

    fun nearbyZones(point: LatLng): List<ParkingZone> = Geometry.nearbyZones(point, allZones)

    private companion object {
        const val KEY_DAY = "epoch_day"
    }
}
