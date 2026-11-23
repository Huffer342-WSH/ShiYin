package com.Huffer342.shiyin

import android.content.ClipboardManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Huffer342.shiyin.ui.ManageSongsSheet
import com.Huffer342.shiyin.ui.LibraryScreen
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

    @Test fun songMenuOpensAudioDetails() {
        val song = Song("content://media/external/audio/media/1", "Details", "Artist", 65_000)
        compose.setContent {
            ShiYinTheme {
                LibraryScreen(
                    listOf(song), PlaybackUi(), true, true, false, PaddingValues(),
                    rememberLazyListState(), "", {}, {}, { _, _ -> }, {}, {}, { _, _ -> }
                )
            }
        }
        compose.onNodeWithContentDescription("Details，更多操作").performClick()
        compose.onNodeWithText("详细信息").performClick()
        compose.onNodeWithText("音频详细信息").assertExists()
        compose.onNodeWithText("标题").assertExists()
        compose.onNodeWithText("Details").assertExists()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("audio_location").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("audio_location").performClick()
        compose.runOnIdle {
            val clipboard = InstrumentationRegistry.getInstrumentation().targetContext
                .getSystemService(ClipboardManager::class.java)
            assertTrue(clipboard.hasPrimaryClip())
        }
        compose.onNodeWithTag("audio_location").performTouchInput { longClick() }
        compose.onNodeWithText("打开文件管理器？").assertExists()
    }

    @Test fun songSortMenuChangesCriterionAndDirection() {
        val zulu = Song("content://media/external/audio/media/1", "Zulu", "Artist", 1000, "z.mp3", "Music/Z/z.mp3")
        val alpha = Song("content://media/external/audio/media/2", "Alpha", "Artist", 1000, "a.mp3", "Music/A/a.mp3")
        compose.setContent {
            ShiYinTheme {
                LibraryScreen(
                    listOf(zulu, alpha), PlaybackUi(), true, true, false, PaddingValues(),
                    rememberLazyListState(), "", {}, {}, { _, _ -> }, {}, {}, { _, _ -> }
                )
            }
        }

        compose.onNodeWithContentDescription("排序").performClick()
        compose.onNodeWithText("文件路径").performClick()
        compose.onNodeWithText("2 首 · 文件路径升序").assertExists()
        assertTrue(compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText("Zulu").fetchSemanticsNode().boundsInRoot.top)

        compose.onNodeWithContentDescription("排序").performClick()
        compose.onNodeWithText("切换为降序").performClick()
        compose.onNodeWithText("2 首 · 文件路径降序").assertExists()
        assertTrue(compose.onNodeWithText("Zulu").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.top)

        compose.onNodeWithContentDescription("分类").performClick()
        compose.onNodeWithText("文件夹").performClick()
        compose.onNodeWithText("2 首 · 文件路径降序 · 文件夹").assertExists()
        compose.onNodeWithText("Music/A").assertExists()
        compose.onNodeWithText("Music/Z").assertExists()
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
