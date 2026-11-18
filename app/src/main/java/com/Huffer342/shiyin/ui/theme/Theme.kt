package com.Huffer342.shiyin.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.Huffer342.shiyin.*

@Composable
fun ShiYinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    settings: ThemeSettings = ThemeSettings(wallpaper = dynamicColor),
    content: @Composable () -> Unit
) {
    val dark = when (settings.mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; else -> darkTheme }
    val base = if (dark) DarkColors else LightColors
    val colors = if (settings.wallpaper && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (settings.palette == ThemePalette.FOREST) base
    else {
        val seed = settings.seed
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val primary = accessibleAccent(seed, dark)
        val container = blendColor(seed, if (dark) black else white, if (dark) .48f else .84f)
        val foreground = if (colorContrast(primary, white) >= 4.5) white else black
        val containerText = when {
            colorContrast(container, white) >= 4.5 -> white
            settings.palette == ThemePalette.OCEAN -> 0xFF173F78.toInt()
            else -> black
        }
        val pageColor = if (!dark && settings.palette == ThemePalette.OCEAN) Color.White
            else Color(blendColor(seed, if (dark) black else white, if (dark) .94f else .97f))
        base.copy(
            primary = Color(primary), onPrimary = Color(foreground),
            primaryContainer = Color(container), onPrimaryContainer = Color(containerText),
            secondary = Color(primary), onSecondary = Color(foreground),
            secondaryContainer = Color(blendColor(container, if (dark) black else white, .18f)),
            onSecondaryContainer = Color(containerText),
            tertiary = Color(primary), onTertiary = Color(foreground),
            tertiaryContainer = Color(container), onTertiaryContainer = Color(containerText),
            background = pageColor, surface = pageColor,
            onBackground = Color(if (dark) 0xFFF0F0F0 else 0xFF191919),
            onSurface = Color(if (dark) 0xFFF0F0F0 else 0xFF191919),
            onSurfaceVariant = Color(if (dark) 0xFFD0D0D0 else 0xFF484848),
            surfaceVariant = Color(blendColor(seed, if (dark) black else white, .82f)),
            surfaceContainerLowest = Color(blendColor(seed, if (dark) black else white, .98f)),
            surfaceContainerLow = Color(blendColor(seed, if (dark) black else white, .94f)),
            surfaceContainer = Color(blendColor(seed, if (dark) black else white, .90f)),
            surfaceContainerHigh = Color(blendColor(seed, if (dark) black else white, .86f)),
            surfaceContainerHighest = Color(blendColor(seed, if (dark) black else white, .82f)),
            outline = Color(blendColor(seed, if (dark) white else black, .55f)),
            outlineVariant = Color(blendColor(seed, if (dark) black else white, .72f)),
            surfaceTint = Color(primary)
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? ComponentActivity)?.let { activity ->
            val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            if (Build.VERSION.SDK_INT >= 29) activity.window.isNavigationBarContrastEnforced = false
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content)
}
