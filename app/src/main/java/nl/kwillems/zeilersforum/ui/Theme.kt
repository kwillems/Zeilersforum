package nl.kwillems.zeilersforum.ui

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
import nl.kwillems.zeilersforum.data.ThemeMode

// Kleuren afgeleid van het blauw van het Zeilersforum-logo.
private val Light = lightColorScheme(
    primary = Color(0xFF2F5F98),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001B3C),
    secondary = Color(0xFF00658E),
    secondaryContainer = Color(0xFFC6E7FF),
    tertiary = Color(0xFF6B5778),
    surface = Color(0xFFF9F9FF),
    surfaceContainer = Color(0xFFEDEDF4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF003061),
    primaryContainer = Color(0xFF13467F),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFF82CFFF),
    secondaryContainer = Color(0xFF004C6C),
    tertiary = Color(0xFFD7BEE4),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2024),
)

@Composable
fun ZfTheme(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
