package hu.mobilparkolas.notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hu.mobilparkolas.domain.model.ParkingSession
import hu.mobilparkolas.domain.sms.SmsPlan
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Notifications for an active parking session:
 *  - an ongoing notification (also used as the foreground-service notification), and
 *  - a "you returned to the vehicle" notification with a one-tap STOP action.
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
    fun buildOngoingNotification(session: ParkingSession, stopPlan: SmsPlan): Notification {
        val pendingState = session.isPending()
        val effectiveMillis = session.effectiveStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openPending)
            .setCategory(Notification.CATEGORY_STATUS)
            .addAction(0, if (pendingState) "Ütemezés visszavonása" else "Leállítás", stopPendingIntent(stopPlan))

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

    fun showOngoing(session: ParkingSession, stopPlan: SmsPlan) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching {
                NotificationManagerCompat.from(context).notify(NOTIF_ID, buildOngoingNotification(session, stopPlan))
            }
        }
    }

    private fun stopPendingIntent(stopPlan: SmsPlan): PendingIntent {
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${stopPlan.number}")).apply {
            putExtra("sms_body", stopPlan.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context, 2003, smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Shown when we detect the user is back at the vehicle; one tap pre-fills the STOP SMS. */
    fun showReturnNotification(stopPlan: SmsPlan, session: ParkingSession) {
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${stopPlan.number}")).apply {
            putExtra("sms_body", stopPlan.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val stopPending = PendingIntent.getActivity(
            context, 2001, smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 2002, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, RETURN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Visszatértél a járműhöz?")
            .setContentText("Parkolás leállítása — ${session.plate}")
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPending)
            .addAction(0, "Leállítás (STOP)", stopPending)
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

    companion object {
        const val CHANNEL_ID = "parking_active"
        const val NOTIF_ID = 1001
        private const val RETURN_CHANNEL_ID = "parking_return"
        private const val RETURN_NOTIF_ID = 1002
        private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    }
}
