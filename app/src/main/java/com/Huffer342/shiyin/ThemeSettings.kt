package com.Huffer342.shiyin

import kotlin.math.pow
import kotlin.math.roundToInt

enum class ThemePalette(val label: String, val seed: Int) {
    FOREST("森林", 0xFF286558.toInt()), OCEAN("海蓝", 0xFF2984F0.toInt()),
    VIOLET("暮紫", 0xFF72569A.toInt()), AMBER("暖橙", 0xFFA34F25.toInt()),
    CUSTOM("自定义", 0xFF2984F0.toInt())
}
enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

data class ThemeSettings(
    val palette: ThemePalette = ThemePalette.OCEAN,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val customHex: String = "#2984F0",
    val wallpaper: Boolean = false
) {
    init { require(parseThemeColor(customHex) != null) { "请输入 #RRGGBB 格式的颜色" } }
    val seed: Int get() = if (palette == ThemePalette.CUSTOM) parseThemeColor(customHex)!! else palette.seed
}

fun parseThemeColor(text: String): Int? =
    if (Regex("#[0-9a-fA-F]{6}").matches(text.trim())) (0xFF000000L or text.trim().drop(1).toLong(16)).toInt() else null

fun blendColor(first: Int, second: Int, amount: Float): Int {
    val weight = amount.coerceIn(0f, 1f)
    fun channel(shift: Int): Int = (((first ushr shift and 255) * (1 - weight)) +
        ((second ushr shift and 255) * weight)).roundToInt()
    return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

fun colorContrast(first: Int, second: Int): Double {
    fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val x = (color ushr shift and 255) / 255.0
            return if (x <= .04045) x / 12.92 else ((x + .055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
    val a = luminance(first)
    val b = luminance(second)
    return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
}

fun accessibleAccent(seed: Int, dark: Boolean): Int {
    val background = if (dark) 0xFF141B17.toInt() else 0xFFFAF8F3.toInt()
    val target = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    for (step in 0..20) {
        val candidate = blendColor(seed, target, step / 20f)
        if (colorContrast(candidate, background) >= 4.5) return candidate
    }
    return target
}
