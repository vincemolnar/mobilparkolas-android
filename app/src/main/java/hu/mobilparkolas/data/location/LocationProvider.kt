package hu.mobilparkolas.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import hu.mobilparkolas.domain.model.LatLng
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper over FusedLocationProviderClient. Callers must ensure the location
 * permission has been granted before invoking [current].
 */
class LocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun current(): LatLng? {
        val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            ?: client.lastLocation.await()
            ?: return null
        return LatLng(loc.latitude, loc.longitude)
    }
}
