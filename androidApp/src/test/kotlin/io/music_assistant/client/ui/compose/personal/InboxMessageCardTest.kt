package io.music_assistant.client.ui.compose.personal

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class InboxMessageCardTest {
    @get:Rule val compose = createComposeRule()
    private val unread = InboxMessage("one", "austen", SharedAlbum("Barnstorm", listOf("Paul Spring")), "", 100.0, null)

    @Test fun `secondary actions are in menu and dispatch once without opening album`() {
        var opened = 0; var found = 0; var read = 0; var archived = 0
        compose.setContent { AppTheme { InboxMessageCard(unread, null, true, true,
            { opened++ }, { read++ }, { archived++ }, { found++ }) } }
        compose.onNodeWithText("Open album").assertIsDisplayed()
        compose.onNodeWithText("Archive").assertDoesNotExist()
        compose.onNodeWithContentDescription("More actions for Barnstorm").performClick()
        compose.onNodeWithText("Find album").performClick()
        assertEquals(1, found); assertEquals(0, opened); assertEquals(0, read); assertEquals(0, archived)
        compose.onNodeWithContentDescription("More actions for Barnstorm").performClick()
        compose.onNodeWithText("Mark read").performClick()
        assertEquals(1, read)
    }
    @Test fun `read message excludes mark read and unavailable writes stay disabled`() {
        compose.setContent { AppTheme { InboxMessageCard(unread.copy(readAt = 101.0), null, false, true, {}, {}, {}, {}) } }
        compose.onNodeWithText("Open album").assertIsNotEnabled()
        compose.onNodeWithContentDescription("More actions for Barnstorm").performClick()
        compose.onNodeWithText("Mark read").assertDoesNotExist()
        compose.onNodeWithText("Find album").assertIsEnabled()
        compose.onNodeWithText("Archive").assertIsNotEnabled()
    }
    @Test fun `small width and large text retain compact touch targets`() {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
            AppTheme { androidx.compose.foundation.layout.Box(Modifier.width(320.dp)) {
                InboxMessageCard(unread.copy(album = SharedAlbum("A very long album title spanning multiple lines", listOf("A very long artist name"))), null, true, true, {}, {}, {}, {})
            } }
        } }
        compose.onNodeWithText("Open album").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("More actions for A very long album title spanning multiple lines")
            .assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        assertTrue(compose.onNodeWithTag("InboxMessage-one").fetchSemanticsNode().boundsInRoot.height <= 240f)
    }
}
