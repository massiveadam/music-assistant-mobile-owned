package io.music_assistant.client.ui.compose.personal

import io.music_assistant.client.ui.theme.AppTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxUnreadBadgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `notification appears and clears as unread albums change`() {
        val count = mutableIntStateOf(0)
        compose.setContent { AppTheme { InboxShortcut(count.intValue) {} } }
        compose.onNodeWithText("Inbox").assertIsDisplayed()
        compose.onNodeWithText("3", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { count.intValue = 3 }
        compose.onNodeWithText("3", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("3 unread albums", useUnmergedTree = true).assertExists()
        compose.runOnIdle { count.intValue = 0 }
        compose.onNodeWithText("3", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun `large counts stay compact and expose the full count`() {
        compose.setContent { AppTheme { InboxUnreadBadge(120) } }
        compose.onNodeWithText("99+").assertIsDisplayed()
        compose.onNodeWithContentDescription("120 unread albums").assertExists()
    }
}
