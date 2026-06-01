package hu.mobilparkolas.notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hu.mobilparkolas.domain.model.ParkingSession
import hu.mobilparkolas.ui.MainActivity
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Notifications for an active parking session:
 *  - an ongoing notification (also used as the foreground-service notification), and
 *  - a "you returned to the vehicle" notification.
 *
 * The stop/cancel action routes through [MainActivity] (EXTRA_STOP_PARK) so the app both
 * records the stop AND opens the STOP SMS — tapping it in the notification keeps the app
 * state in sync (it does not just open the SMS app).
 */
class ParkingNotifier(private val context: Context) {

    fun ensureChannel() {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Folyamatban lévő parkolás", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        mgr.createNotificationChannel(
            NotificationChannel(RETURN_CHANNEL_ID, "Visszatérés a járműhöz", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** The ongoing notification; reused as the foreground-service notification. */
    fun buildOngoingNotification(session: ParkingSession): Notification {
        val pendingState = session.isPending()
        val effectiveMillis = session.effectiveStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())
            .setCategory(Notification.CATEGORY_STATUS)
            .addAction(0, if (pendingState) "Ütemezés visszavonása" else "Leállítás", stopPendingIntent())

        if (pendingState) {
            builder.setContentTitle("Parkolás ütemezve")
                .setContentText("Zóna ${session.zoneCode} • ${session.plate} • kezdés: ${session.scheduledStart?.format(FMT)}")
        } else {
            builder.setContentTitle("Parkolás folyamatban")
                .setContentText("Zóna ${session.zoneCode} • ${session.plate}")
                .setUsesChronometer(true)
                .setWhen(effectiveMillis)
        }
        return builder.build()
    }

    fun showOngoing(session: ParkingSession) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, buildOngoingNotification(session)) }
        }
    }

    /** Shown when we detect the user is back at the vehicle. */
    fun showReturnNotification(session: ParkingSession) {
        val notification = NotificationCompat.Builder(context, RETURN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Visszatértél a járműhöz?")
            .setContentText("Parkolás leállítása — ${session.plate}")
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppPendingIntent())
            .addAction(0, "Leállítás (STOP)", stopPendingIntent())
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(RETURN_NOTIF_ID, notification) }
        }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).apply {
            cancel(NOTIF_ID)
            cancel(RETURN_NOTIF_ID)
        }
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Routes through MainActivity so the app records the stop and then opens the STOP SMS. */
    private fun stopPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "hu.mobilparkolas.STOP_FROM_NOTIFICATION"
            putExtra(MainActivity.EXTRA_STOP_PARK, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context, 2003, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "parking_active"
        const val NOTIF_ID = 1001
        private const val RETURN_CHANNEL_ID = "parking_return"
        private const val RETURN_NOTIF_ID = 1002
        private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    }
}
