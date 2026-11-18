package com.Huffer342.shiyin

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.Huffer342.shiyin.ui.ManageSongsSheet
import com.Huffer342.shiyin.ui.ThemeScreen
import com.Huffer342.shiyin.ui.theme.ShiYinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectingSongsAddsToExistingPlaylist() {
        val first = Song("content://media/external/audio/media/1", "First", "", 1000)
        val second = Song("content://media/external/audio/media/2", "Second", "", 1000)
        var saved: List<Song>? = null
        compose.setContent {
            ShiYinTheme { ManageSongsSheet(MusicPlaylist(name = "List", songs = listOf(first)), listOf(first, second), {}, { saved = it }) }
        }
        compose.onNodeWithText("Second").performClick()
        compose.onNodeWithText("完成（2）").performClick()
        compose.runOnIdle { assertEquals(listOf(first, second), saved) }
    }

    @Test fun customThemeRejectsBadHexAndAppliesValidColor() {
        var result = ThemeSettings()
        compose.setContent {
            var value by remember { mutableStateOf(result) }
            ShiYinTheme(settings = value) {
                ThemeScreen(value, PaddingValues()) { value = it; result = it }
            }
        }
        compose.onNodeWithTag("theme_screen").performScrollToNode(hasText("自定义"))
        compose.onNodeWithText("自定义").performClick()
        compose.onNodeWithTag("theme_hex").performTextReplacement("#GG0000")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("请输入六位十六进制颜色").assertExists()
        compose.runOnIdle { assertEquals(ThemePalette.OCEAN, result.palette) }
        compose.onNodeWithTag("theme_hex").performTextReplacement("#336699")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals(ThemePalette.CUSTOM, result.palette)
            assertEquals("#336699", result.customHex)
        }
    }
}
