package com.example.wavebridge.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = BridgeAqua,
    onPrimary = BridgeInk,
    primaryContainer = Color(0xFF113B37),
    onPrimaryContainer = Color(0xFFC7FFF4),
    secondary = BridgeBlue,
    onSecondary = Color(0xFF09122F),
    secondaryContainer = Color(0xFF1C2D5E),
    onSecondaryContainer = Color(0xFFDDE4FF),
    tertiary = BridgeAmber,
    onTertiary = Color(0xFF2A1800),
    tertiaryContainer = Color(0xFF4B3210),
    onTertiaryContainer = Color(0xFFFFE0AD),
    background = BridgeInk,
    onBackground = Color(0xFFE3EFEC),
    surface = BridgePanel,
    onSurface = Color(0xFFE3EFEC),
    surfaceVariant = BridgePanelHigh,
    onSurfaceVariant = Color(0xFFADC5C0),
    outline = Color(0xFF315552),
    error = BridgeError,
)

private val LightColorScheme = lightColorScheme(
    primary = BridgeAquaDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4FFF3),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = BridgeBlueDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4FF),
    onSecondaryContainer = Color(0xFF001849),
    tertiary = BridgeAmberDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2E1600),
    background = BridgePaper,
    onBackground = Color(0xFF15201E),
    surface = Color.White,
    onSurface = Color(0xFF15201E),
    surfaceVariant = BridgeMist,
    onSurfaceVariant = Color(0xFF4B635F),
    outline = Color(0xFF7A938E),
    error = Color(0xFFBA1A1A),
)

private val WaveBridgeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun WaveBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = WaveBridgeShapes,
        content = content
    )
}
