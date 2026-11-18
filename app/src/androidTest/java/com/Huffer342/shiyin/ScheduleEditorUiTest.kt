package com.Huffer342.shiyin

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.Huffer342.shiyin.ui.ScheduleEditorScreen
import com.Huffer342.shiyin.ui.theme.ShiYinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private val song = Song("content://media/external/audio/media/1", "测试音乐", "测试艺术家", 60_000)
    private var saved: Schedule? = null
    private var closed = false

    private fun content(restoration: StateRestorationTester? = null) {
        val screen: @androidx.compose.runtime.Composable () -> Unit = {
            ShiYinTheme {
                ScheduleEditorScreen(null, song, listOf(song), false, { false }, listOf(2026),
                    { closed = true }, { _, _ -> }, {}, { saved = it })
            }
        }
        if (restoration == null) compose.setContent(screen) else restoration.setContent(screen)
    }

    @Test fun invalidDurationDoesNotSaveOrClose() {
        content()
        compose.onNodeWithTag("schedule_editor").performScrollToNode(hasTestTag("duration"))
        compose.onNodeWithTag("duration").performTextReplacement("0")
        compose.onNodeWithTag("save_schedule").performClick()
        compose.onNodeWithTag("schedule_editor").performScrollToNode(hasTestTag("editor_error"))
        compose.onNodeWithTag("editor_error").assertTextEquals("播放时长须为 1–1440 分钟")
        compose.runOnIdle { assertNull(saved); assertFalse(closed) }
    }

    @Test fun changedDraftRequiresDiscardConfirmation() {
        content()
        compose.onNodeWithTag("schedule_editor").performScrollToNode(hasTestTag("duration"))
        compose.onNodeWithTag("duration").performTextReplacement("45")
        compose.onNodeWithContentDescription("取消编辑").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.runOnIdle { assertFalse(closed) }
        compose.onNodeWithContentDescription("取消编辑").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.runOnIdle { assertTrue(closed); assertNull(saved) }
    }

    @Test fun draftSurvivesStateRestorationAndSavesSelectedDuration() {
        val restoration = StateRestorationTester(compose)
        content(restoration)
        compose.onNodeWithTag("schedule_editor").performScrollToNode(hasTestTag("duration"))
        compose.onNodeWithTag("duration").performTextReplacement("45")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("schedule_editor").performScrollToNode(hasTestTag("duration"))
        compose.onNodeWithTag("duration").assertTextContains("45")
        compose.onNodeWithTag("save_schedule").performClick()
        compose.runOnIdle {
            assertEquals(45, saved?.stopMinutes)
            assertEquals(song.uri, saved?.uri)
        }
    }
}
