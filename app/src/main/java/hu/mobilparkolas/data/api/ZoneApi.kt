package hu.mobilparkolas.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface ZoneApi {

    /**
     * Returns parking zones. An empty [search] returns *all* zones nationwide
     * (~1.3 MB). [time] must be "yyyy-MM-dd HH:mm:ss"; the server computes each
     * zone's `timetable`/`fee` relative to it.
     */
    @GET("parking_purchases/search_parking_zones/")
    suspend fun searchZones(
        @Query("search") search: String,
        @Query("time") time: String,
    ): List<ZoneDto>
}
