package hu.mobilparkolas.data.api

import hu.mobilparkolas.domain.model.LatLng
import hu.mobilparkolas.domain.model.ParkingZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One {lat, long} pair. Coordinates come back as strings in the JSON. */
@Serializable
data class CoordDto(
    @SerialName("lat") val lat: String,
    @SerialName("long") val lng: String,
)

/** Raw zone object from /parking_purchases/search_parking_zones/. */
@Serializable
data class ZoneDto(
    @SerialName("id") val id: String? = null,
    @SerialName("zoneid") val zoneId: String? = null,
    @SerialName("telepules") val telepules: String? = null,
    @SerialName("fee") val fee: Int = 0,
    @SerialName("service_na") val serviceName: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("timetable") val timetable: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("geometry") val geometry: List<CoordDto> = emptyList(),
)

fun ZoneDto.toDomainOrNull(): ParkingZone? {
    val zid = zoneId ?: return null
    val polygon = geometry.mapNotNull { c ->
        val lat = c.lat.toDoubleOrNull()
        val lng = c.lng.toDoubleOrNull()
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }
    if (polygon.size < 3) return null
    return ParkingZone(
        id = id ?: zid,
        zoneCode = zid,
        city = telepules.orEmpty(),
        feeHuf = fee,
        serviceName = serviceName.orEmpty(),
        timetableRaw = timetable.orEmpty(),
        colorHex = color ?: "#888888",
        polygon = polygon,
    )
}
