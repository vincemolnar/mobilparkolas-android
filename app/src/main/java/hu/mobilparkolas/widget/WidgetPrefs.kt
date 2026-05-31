package hu.mobilparkolas.widget

import android.content.Context

/** Per-widget configuration: which car a given widget instance uses (null = default car). */
object WidgetPrefs {
    private const val PREFS = "widget_prefs"
    private fun key(appWidgetId: Int) = "car_$appWidgetId"

    fun setCar(context: Context, appWidgetId: Int, carId: Long?) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (carId == null) p.remove(key(appWidgetId)) else p.putLong(key(appWidgetId), carId)
        p.apply()
    }

    fun getCar(context: Context, appWidgetId: Int): Long? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.contains(key(appWidgetId))) p.getLong(key(appWidgetId), -1L) else null
    }

    fun clear(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(appWidgetId)).apply()
    }
}
