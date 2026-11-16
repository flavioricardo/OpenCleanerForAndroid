package io.github.flavioricardo.opencleaner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same palette as OpenCleaner for Windows: gray page, white cards, green brand, amber for attention.
private val Light = lightColorScheme(
    primary = Color(0xFF0E7C66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF1EC),
    onPrimaryContainer = Color(0xFF0A4F41),
    secondary = Color(0xFF0E7C66),
    tertiary = Color(0xFFB7791F),
    background = Color(0xFFEEF1F4),
    onBackground = Color(0xFF1F2328),
    surface = Color.White,
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFE6EAEE),
    onSurfaceVariant = Color(0xFF5B636E),
    surfaceContainer = Color.White,
    outlineVariant = Color(0xFFDDE2E7),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5CC8AE),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF113D33),
    onPrimaryContainer = Color(0xFFB5EBDD),
    secondary = Color(0xFF5CC8AE),
    tertiary = Color(0xFFE3A64B),
    background = Color(0xFF111417),
    onBackground = Color(0xFFE4E7EA),
    surface = Color(0xFF1B1F23),
    onSurface = Color(0xFFE4E7EA),
    surfaceVariant = Color(0xFF2A3036),
    onSurfaceVariant = Color(0xFFA3ABB4),
    surfaceContainer = Color(0xFF1B1F23),
    outlineVariant = Color(0xFF30373D),
)

@Composable
fun OpenCleanerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
