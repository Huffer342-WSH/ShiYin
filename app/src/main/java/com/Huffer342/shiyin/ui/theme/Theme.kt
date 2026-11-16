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
    settings: ThemeSettings? = null,
    content: @Composable () -> Unit
) {
    val dark = when (settings?.mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; else -> darkTheme }
    val base = if (dark) DarkColors else LightColors
    val colors = if ((settings?.wallpaper ?: dynamicColor) && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (settings == null || settings.palette == ThemePalette.FOREST) base
    else {
        val seed = settings.seed
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val primary = accessibleAccent(seed, dark)
        val container = blendColor(seed, if (dark) black else white, if (dark) .48f else .84f)
        val foreground = if (colorContrast(primary, white) >= 4.5) white else black
        val containerText = if (colorContrast(container, white) >= 4.5) white else black
        base.copy(
            primary = Color(primary), onPrimary = Color(foreground),
            primaryContainer = Color(container), onPrimaryContainer = Color(containerText),
            secondary = Color(primary), onSecondary = Color(foreground),
            secondaryContainer = Color(blendColor(container, if (dark) black else white, .18f)),
            onSecondaryContainer = Color(containerText),
            tertiary = Color(primary), onTertiary = Color(foreground),
            tertiaryContainer = Color(container), onTertiaryContainer = Color(containerText),
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
