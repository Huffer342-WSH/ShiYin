package com.Huffer342.shiyin

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.Huffer342.shiyin.ui.FolderListScreen
import com.Huffer342.shiyin.ui.MediaStoreDirectoryScreen
import com.Huffer342.shiyin.ui.theme.ShiYinTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemLibraryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun directoryPageShowsEnabledSystemLibraryAlongsideCustomFolders() {
        val folder = WatchedFolder(
            "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            "My Music"
        )
        compose.setContent {
            ShiYinTheme {
                FolderListScreen(
                    true, true, 12,
                    listOf(MediaStoreDirectory("external_primary|recordings/", "external_primary", "Recordings/", 3, false)),
                    "", listOf(folder), listOf(FolderScan(folder, emptyList())), PaddingValues(),
                    {}, {}, {}, {}, {}
                )
            }
        }

        compose.onNodeWithText("系统音乐库").assertExists()
        compose.onNodeWithTag("system_library_switch").assertIsOn()
        compose.onNodeWithText("12 首 · 已排除 1 个目录").assertExists()
        compose.onNodeWithText("My Music").assertExists()
    }

    @Test
    fun excludedDirectoryCanBeEnabled() {
        val directory = MediaStoreDirectory(
            "external_primary|recordings/",
            "external_primary",
            "Recordings/",
            3,
            false
        )
        var enabled = false
        compose.setContent {
            ShiYinTheme {
                MediaStoreDirectoryScreen(
                    true, true, listOf(directory), PaddingValues(), {}, {}, {},
                    { value, included -> assertTrue(value === directory); enabled = included }
                )
            }
        }

        compose.onNodeWithText("Recordings/").assertExists()
        compose.onNodeWithTag("media_directory_${directory.key}").assertIsOff().performClick()
        compose.runOnIdle { assertTrue(enabled) }
    }
}
