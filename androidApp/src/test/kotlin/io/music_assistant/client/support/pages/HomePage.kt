package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.home.HomeScreenSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.cd_album_item
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings

class HomePage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithContentDescription(Res.string.nav_home.get()).assertIsDisplayed()
        assertNavBar(
            items = listOf(
                Res.string.nav_home.get(),
                Res.string.nav_library.get(),
                Res.string.nav_search.get(),
                Res.string.nav_settings.get(),
            ),
            selected = Res.string.nav_home.get(),
        )
    }

    fun clickOnMedia(item: ServerMediaItem, withinTag: String? = null): ItemPage {
        return clickOnMedia(item, Res.string.nav_home.get(), withinTag)
    }

    fun refresh(): HomePage {
        // Recommendation assertions can scroll the collapsing toolbar out of view.
        if (composeTestRule.onAllNodesWithTag(HomeScreenSemantics.LIST_TAG).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithTag(HomeScreenSemantics.LIST_TAG).performScrollToIndex(0)
            composeTestRule.onNodeWithTag(HomeScreenSemantics.LIST_TAG).performTouchInput { swipeDown() }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Refresh").assertIsDisplayed().performClick()
        return this
    }

    fun clickOnShortcut(item: ServerMediaItem): ItemPage {
        return clickOnMedia(
            item,
            Res.string.nav_home.get(),
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
        } } catch (e: Exception) {
            throw AssertionError("${lastError?.message}\n${composeTestRule.onRoot().printToString()}", e)
        }
        return this
    }

    fun assertErrorLoadingData(): HomePage {
        composeTestRule.waitUntil {
            composeTestRule.onNodeWithText(Res.string.library_error.get()).isDisplayed()
        }
        return this
    }
}
