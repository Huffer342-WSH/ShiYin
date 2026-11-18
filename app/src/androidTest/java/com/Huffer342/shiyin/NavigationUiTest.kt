package com.Huffer342.shiyin

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun destinationsAndBackKeepLibrarySearch() {
        if (compose.onAllNodesWithText("稍后设置").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("稍后设置").performClick()
        compose.onNodeWithTag("background_swipe").assertExists()
        if (compose.onAllNodesWithText("知道了").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithText("时音").assertDoesNotExist()
        compose.onNodeWithText("随身音乐").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索").performClick()
        compose.onNodeWithTag("library_search").performTextInput("navigation-state-check")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("nav_1").performClick()
        compose.onNodeWithTag("playlist_list").assertExists()
        compose.onNodeWithTag("nav_2").performClick()
        compose.onNodeWithTag("folder_list").assertExists()
        compose.onNodeWithTag("main_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav_3").assertIsSelected()
        compose.onNodeWithTag("main_pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("nav_2").assertIsSelected()
        Espresso.pressBack()
        compose.onNodeWithTag("library_search").assertTextContains("navigation-state-check")
        compose.onNodeWithContentDescription("清空搜索").performClick()
        compose.onNodeWithTag("library_search").assert(hasText("navigation-state-check").not())
    }
}
