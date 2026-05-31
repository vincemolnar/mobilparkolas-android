package hu.mobilparkolas.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import hu.mobilparkolas.MobilParkolasApp
import hu.mobilparkolas.R
import hu.mobilparkolas.ui.MainActivity
import kotlinx.coroutines.runBlocking

/**
 * Home-screen widget: a single tap launches the app in quick-park mode (see
 * MainActivity). Each widget instance can target a specific car (else the default).
 * The widget shows the car's plate; when narrow, only the plate.
 */
class ParkingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, manager, id) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val carId = WidgetPrefs.getCar(context, appWidgetId)
            val plate = resolvePlate(context, carId)

            val intent = Intent(context, MainActivity::class.java).apply {
                // Unique action per widget so PendingIntents are not collapsed.
                action = "hu.mobilparkolas.WIDGET_QUICK_PARK_$appWidgetId"
                putExtra(MainActivity.EXTRA_QUICK_PARK, true)
                if (carId != null) putExtra(MainActivity.EXTRA_CAR_ID, carId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pending = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            fun layout(resId: Int, fallback: String) =
                RemoteViews(context.packageName, resId).apply {
                    setOnClickPendingIntent(R.id.widget_root, pending)
                    setTextViewText(R.id.widget_plate, plate ?: fallback)
                }

            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RemoteViews(
                    mapOf(
                        // narrow: plate only (no icon)
                        SizeF(40f, 40f) to layout(R.layout.widget_quick_park_small, "P"),
                        // short / half height: icon + plate, no label
                        SizeF(120f, 40f) to layout(R.layout.widget_quick_park_medium, "Parkolás"),
                        // tall + wide: icon + label + plate
                        SizeF(150f, 80f) to layout(R.layout.widget_quick_park, "Parkolás"),
                    )
                )
            } else {
                layout(R.layout.widget_quick_park, "Parkolás")
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun resolvePlate(context: Context, carId: Long?): String? {
            val locator = (context.applicationContext as MobilParkolasApp).locator
            val car = runBlocking {
                (carId?.let { locator.carRepository.getById(it) }) ?: locator.carRepository.getDefault()
            }
            return car?.plate
        }
    }
}
