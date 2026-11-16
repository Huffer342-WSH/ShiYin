package com.Huffer342.shiyin

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.Huffer342.shiyin.ui.FolderBrowserScreen
import com.Huffer342.shiyin.ui.theme.ShiYinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderBrowserUiTest {
    @get:Rule val compose = createComposeRule()
    private val a = Song("content://media/external/audio/media/1", "A", "", 1000)
    private val b = Song("content://media/external/audio/media/2", "B", "", 1000)
    private val c = Song("content://media/external/audio/media/3", "C", "", 1000)
    private val playlist = MusicPlaylist(name = "Target")
    private var added = emptyList<Song>()

    private fun show() {
        val folder = WatchedFolder("content://com.android.externalstorage.documents/tree/primary%3AMusic", "Music")
        val scan = FolderScan(folder, listOf(a, b, c), entries = listOf(
            FolderEntry("child", "primary:Music", "Child"),
            FolderEntry("a", "primary:Music", "A", a),
            FolderEntry("b", "child", "B", b),
            FolderEntry("c", "child", "C", c)))
        compose.setContent {
            ShiYinTheme {
                FolderBrowserScreen(scan, PaddingValues(), listOf(playlist), {}, { _, _ -> },
                    { id, songs, done -> assertEquals(playlist.id, id); added = songs; done() },
                    { _, _, _ -> })
            }
        }
    }

    @Test fun hierarchyAndSelectAllUseCurrentView() {
        show()
        compose.onNodeWithText("B").assertDoesNotExist()
        compose.onNodeWithText("Child").performClick()
        compose.onNodeWithText("A").assertDoesNotExist()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("已选 2").assertExists()
        compose.onNodeWithContentDescription("添加到播放列表").performClick()
        compose.onNodeWithText("Target").performClick()
        compose.runOnIdle { assertEquals(listOf(b, c), added) }
        compose.onNodeWithContentDescription("上一级").performClick()
        compose.onNodeWithText("A").assertExists()
        compose.onNodeWithText("全部音频").performClick()
        compose.onNodeWithText("Child").assertDoesNotExist()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("已选 3").assertExists()
    }

    @Test fun longPressDragSelectsRangeAndTapToggles() {
        show()
        compose.onNodeWithText("全部音频").performClick()
        val first = compose.onNodeWithTag("entry_A").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("entry_C").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("folder_entries").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("folder_entries").performTouchInput {
            down(Offset(first.center.x - list.left, first.center.y - list.top))
            advanceEventTime(700)
            moveBy(Offset.Zero)
        }
        compose.onNodeWithTag("folder_entries").performTouchInput {
            moveTo(Offset(last.center.x - list.left, last.center.y - list.top), 300)
            up()
        }
        assertTrue(compose.onRoot().printToString(), compose.onAllNodesWithText("已选 3").fetchSemanticsNodes().size == 1)
        compose.onNodeWithTag("entry_B").performClick()
        compose.onNodeWithText("已选 2").assertExists()
        compose.onNodeWithContentDescription("添加到播放列表").performClick()
        compose.onNodeWithText("Target").performClick()
        compose.runOnIdle { assertEquals(listOf(a, c), added) }
    }
}
