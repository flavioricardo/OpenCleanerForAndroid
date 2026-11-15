package io.github.flavioricardo.opencleaner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF0E7C66)

@Composable
fun OpenCleanerTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF5CC8AE), onPrimary = Color(0xFF00382C), secondary = Brand)
    } else {
        lightColorScheme(primary = Brand, onPrimary = Color.White, secondary = Brand)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
