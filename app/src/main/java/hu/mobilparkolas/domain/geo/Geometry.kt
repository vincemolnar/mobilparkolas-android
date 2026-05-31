package hu.mobilparkolas.domain.geo

import hu.mobilparkolas.domain.model.LatLng
import hu.mobilparkolas.domain.model.ParkingZone

object Geometry {

    /**
     * Ray-casting point-in-polygon test. The polygon is treated as a closed ring
     * (the last vertex is implicitly connected to the first). Coordinates are used
     * directly as planar x=lng, y=lat — adequate for the small extents of a parking
     * zone where Earth curvature is negligible.
     */
    fun pointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val yi = polygon[i].lat
            val xi = polygon[i].lng
            val yj = polygon[j].lat
            val xj = polygon[j].lng
            val intersects = (yi > point.lat) != (yj > point.lat) &&
                point.lng < (xj - xi) * (point.lat - yi) / (yj - yi) + xi
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * Returns all zones whose polygon contains [point]. Uses each zone's bounding
     * box as a cheap pre-filter so the expensive polygon test runs only on candidates.
     * Multiple results mean overlapping/ambiguous zones — the caller should let the
     * user disambiguate rather than guessing.
     */
    fun zonesContaining(point: LatLng, zones: List<ParkingZone>): List<ParkingZone> =
        zones.asSequence()
            .filter { it.bbox.contains(point) }
            .filter { pointInPolygon(point, it.polygon) }
            .toList()

    /**
     * Fallback for uncertain/boundary GPS: zones whose centroid is within
     * [radiusMeters] of the point, nearest first. Used to offer suggestions when
     * [zonesContaining] returns nothing.
     */
    fun nearbyZones(point: LatLng, zones: List<ParkingZone>, radiusMeters: Double = 150.0): List<ParkingZone> =
        zones.asSequence()
            .map { it to haversineMeters(point, centroid(it.polygon)) }
            .filter { it.second <= radiusMeters }
            .sortedBy { it.second }
            .map { it.first }
            .toList()

    fun centroid(polygon: List<LatLng>): LatLng {
        if (polygon.isEmpty()) return LatLng(0.0, 0.0)
        val lat = polygon.sumOf { it.lat } / polygon.size
        val lng = polygon.sumOf { it.lng } / polygon.size
        return LatLng(lat, lng)
    }

    /** Great-circle distance in meters. */
    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val s = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return 2 * r * Math.asin(Math.sqrt(s))
    }
}
