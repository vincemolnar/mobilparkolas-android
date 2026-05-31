package hu.mobilparkolas.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val BASE_URL = "https://szolgaltatasok.nemzetimobilfizetes.hu/"

    /** The server expects "yyyy-MM-dd HH:mm:ss" for the `time` query param. */
    val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun createZoneApi(): ZoneApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ZoneApi::class.java)
    }
}
