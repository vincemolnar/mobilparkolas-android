package hu.mobilparkolas.detect

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts/stops the [ReturnDetectionService] for the duration of an active parking. */
class ReturnDetectionController(private val context: Context) {

    fun start() {
        runCatching {
            ContextCompat.startForegroundService(
                context, Intent(context, ReturnDetectionService::class.java)
            )
        }
    }

    fun stop() {
        runCatching { context.stopService(Intent(context, ReturnDetectionService::class.java)) }
    }
}
