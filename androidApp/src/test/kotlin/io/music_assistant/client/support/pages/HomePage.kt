package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.ScreenSemantics
import io.music_assistant.client.ui.compose.home.HomeScreenSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.discover_edit_rows
import musicassistantclient.composeapp.generated.resources.discover_save_rows
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.nav_discover
import musicassistantclient.composeapp.generated.resources.nav_search

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import musicassistantclient.composeapp.generated.resources.cd_album_item
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_settings

class HomePage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule
            .onNode(hasTestTag(ScreenSemantics.TITLE_TAG))
            .assertIsDisplayed()

        assertNavBar(
            items = listOf(
                Res.string.nav_discover.get(),
                Res.string.nav_search.get(),
            ),
            selected = Res.string.nav_discover.get(),
        )
    }

    fun clickOnMedia(item: ServerMediaItem, withinTag: String? = null): ItemPage {
        return clickOnMedia(item, Res.string.nav_discover.get(), withinTag)
    }

    fun refresh(): HomePage {
        // Re-tapping Discover restores the scrolled header before the real refresh tap.
        composeTestRule.onNodeWithContentDescription(Res.string.nav_discover.get()).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        return this
    }

    fun editRows(): HomePage {
        composeTestRule.onNodeWithContentDescription(Res.string.discover_edit_rows.get()).performClick()
        return this
    }

    fun saveRows(): HomePage {
        composeTestRule.onNodeWithContentDescription(Res.string.discover_save_rows.get()).performClick()
        return this
    }

    fun clickOnShortcut(item: ServerMediaItem): ItemPage {
        return clickOnMedia(
            item,
            Res.string.nav_discover.get(),
            withinTag = HomeScreenSemantics.SHORTCUTS_ROW_TAG,
        )
    }

    fun playShortcut(item: ServerMediaItem): HomePage {
        playMedia(item, withinTag = HomeScreenSemantics.SHORTCUTS_ROW_TAG)
        return this
    }

    fun assertShortcutDisplayed(item: ServerMediaItem): HomePage {
        assertMediaDisplayed(item, withinTag = HomeScreenSemantics.SHORTCUTS_ROW_TAG)
        return this
    }

    fun assertRecommendationDisplayed(item: ServerMediaItem): HomePage {
        val row = hasTestTag(HomeScreenSemantics.rowTag("recently_added_albums"))
        val album = hasContentDescription(Res.string.cd_album_item.get(item.name, item.provider))
        var lastError: Throwable? = null
        try { composeTestRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeTestRule.onNodeWithTag(HomeScreenSemantics.LIST_TAG).performScrollToNode(row)
                composeTestRule.onNode(hasScrollAction() and hasAnyAncestor(row)).performScrollToNode(album)
                composeTestRule.onNodeWithTag(HomeScreenSemantics.LIST_TAG).performScrollToNode(album and hasAnyAncestor(row))
                composeTestRule.onNode(album and hasAnyAncestor(row)).isDisplayed()
            }.onFailure { lastError = it }.getOrDefault(false)
        } } catch (e: Throwable) {
            throw AssertionError("${lastError?.message}\n${composeTestRule.onRoot().printToString()}", e)
        }
        return this
    }

    fun assertErrorLoadingData(): HomePage {
        composeTestRule.waitUntil("library error is displayed") {
            composeTestRule.onNodeWithText(Res.string.library_error.get()).isDisplayed()
        }
        return this
    }
}
