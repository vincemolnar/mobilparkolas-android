package hu.mobilparkolas

import hu.mobilparkolas.domain.geo.Geometry
import hu.mobilparkolas.domain.model.LatLng
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    // A simple square around (47.5, 19.0)
    private val square = listOf(
        LatLng(47.49, 18.99),
        LatLng(47.49, 19.01),
        LatLng(47.51, 19.01),
        LatLng(47.51, 18.99),
    )

    @Test
    fun pointInsideSquare() {
        assertTrue(Geometry.pointInPolygon(LatLng(47.50, 19.00), square))
    }

    @Test
    fun pointOutsideSquare() {
        assertFalse(Geometry.pointInPolygon(LatLng(47.52, 19.00), square))
        assertFalse(Geometry.pointInPolygon(LatLng(47.50, 19.05), square))
    }

    @Test
    fun degeneratePolygonIsNeverInside() {
        assertFalse(Geometry.pointInPolygon(LatLng(0.0, 0.0), listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0))))
    }
}
