package hu.mobilparkolas.ui.sms

import android.content.Context
import android.content.Intent
import android.net.Uri
import hu.mobilparkolas.domain.model.LatLng
import hu.mobilparkolas.domain.sms.SmsPlan

/**
 * Opens the device SMS app pre-filled with recipient + body. We deliberately do NOT
 * use SEND_SMS (restricted on Google Play); the user taps send themselves.
 */
object SmsLauncher {

    fun send(context: Context, plan: SmsPlan) {
        val uri = Uri.parse("smsto:${plan.number}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", plan.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Opens an external maps app to navigate to the parked location. */
    fun navigateTo(context: Context, where: LatLng, label: String = "Parkolás") {
        val uri = Uri.parse("geo:${where.lat},${where.lng}?q=${where.lat},${where.lng}($label)")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
