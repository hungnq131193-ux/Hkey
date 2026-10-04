package com.hkey.app.settings.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 1.4.0 (U2): theme màn cài đặt — dynamic color (API 31+) hoặc
 *  light/dark mặc định theo hệ thống. */

internal val HKeyLightColorScheme = lightColorScheme(
    primary = Color(0xFF007F79),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC3F3EA),
    onPrimaryContainer = Color(0xFF003B38),
    secondary = Color(0xFF415F91),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3FF),
    onSecondaryContainer = Color(0xFF0D2046),
    tertiary = Color(0xFF3E6E8E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCEE5FF),
    onTertiaryContainer = Color(0xFF001D33),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F8F8),
    onBackground = Color(0xFF152D32),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF152D32),
    surfaceVariant = Color(0xFFDCE9EA),
    onSurfaceVariant = Color(0xFF486167),
    outline = Color(0xFF6E8A8F),
    outlineVariant = Color(0xFFC2D4D6),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2A4247),
    inverseOnSurface = Color(0xFFECF3F3),
    inversePrimary = Color(0xFF70DBCE),
    surfaceDim = Color(0xFFD5E0E1),
    surfaceBright = Color(0xFFF4F8F8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F5),
    surfaceContainer = Color(0xFFE9F0F0),
    surfaceContainerHigh = Color(0xFFE3EAEA),
    surfaceContainerHighest = Color(0xFFDDE5E5)
)

internal val HKeyDarkColorScheme = darkColorScheme(
    primary = Color(0xFF70DBCE),
    onPrimary = Color(0xFF003B38),
    primaryContainer = Color(0xFF00534E),
    onPrimaryContainer = Color(0xFFA4F2E6),
    secondary = Color(0xFFA9C8FF),
    onSecondary = Color(0xFF0F3166),
    secondaryContainer = Color(0xFF29487E),
    onSecondaryContainer = Color(0xFFD7E3FF),
    tertiary = Color(0xFFA5CBE3),
    onTertiary = Color(0xFF0B3449),
    tertiaryContainer = Color(0xFF2B4C60),
    onTertiaryContainer = Color(0xFFCEE5FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0C191E),
    onBackground = Color(0xFFE2EFF0),
    surface = Color(0xFF14262C),
    onSurface = Color(0xFFE2EFF0),
    surfaceVariant = Color(0xFF3D5055),
    onSurfaceVariant = Color(0xFFADC5C9),
    outline = Color(0xFF7E989D),
    outlineVariant = Color(0xFF3D5055),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE2EFF0),
    inverseOnSurface = Color(0xFF2A4247),
    inversePrimary = Color(0xFF007F79),
    surfaceDim = Color(0xFF0C191E),
    surfaceBright = Color(0xFF323F44),
    surfaceContainerLowest = Color(0xFF071418),
    surfaceContainerLow = Color(0xFF14262C),
    surfaceContainer = Color(0xFF182A30),
    surfaceContainerHigh = Color(0xFF22343A),
    surfaceContainerHighest = Color(0xFF2C3F45)
)

@Composable
fun HKeyTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) HKeyDarkColorScheme else HKeyLightColorScheme
    MaterialTheme(colorScheme = scheme, content = content)
}
