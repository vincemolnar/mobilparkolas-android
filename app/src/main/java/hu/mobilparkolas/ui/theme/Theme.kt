package hu.mobilparkolas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Blue = Color(0xFF1565C0)
private val BlueDark = Color(0xFF0D47A1)

private val LightColors = lightColorScheme(primary = Blue, secondary = BlueDark)
private val DarkColors = darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFFB0BEC5))

@Composable
fun MobilParkolasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You dynamic color (wallpaper-based) on Android 12+.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
