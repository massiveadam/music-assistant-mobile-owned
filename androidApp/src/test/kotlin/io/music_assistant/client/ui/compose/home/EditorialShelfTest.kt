package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.server.*
import io.music_assistant.client.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-mdpi")
class EditorialShelfTest {
    @get:Rule val compose = createComposeRule()
    private val source = EditorialSource("aoty", "AOTY weekly releases", "aoty", "https://www.albumoftheyear.org/", 100.0)
    private val entry = EditorialEntry("Album", "Artist", "https://example.com/review",
        releaseDate = "2026-10-02", scores = EditorialScores(
            EditorialScore(85.0, 100.0, 12, "reviews"), EditorialScore(3.78, 5.0, 1234, "ratings")))
    private val feed = EditorialFeed(1, source, listOf(entry), 1790985600.0, false,
        "This source note should be available without crowding the albums.",
        EditorialWindow("2026-09-27", "2026-10-03", "America/New_York", "critic"))

    @Test fun `scores and exact totals stay inside artwork with scaled text`() {
        compose.setContent {
            AppTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                    EditorialAlbumCard(entry, Modifier.width(214.dp), {}, {})
                }
            }
        }
        val artwork = compose.onNodeWithTag("editorial-cover:Album").fetchSemanticsNode().boundsInRoot
        listOf("85", "3.78", "12 reviews", "1,234 ratings").forEach { text ->
            val bounds = compose.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= artwork.left && bounds.right <= artwork.right)
            assertTrue(bounds.top >= artwork.top && bounds.bottom <= artwork.bottom)
        }
        compose.onNodeWithTag("editorial-find:Album").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Read review for Album").onParent().assertHeightIsAtLeast(48.dp)
    }

    @Test fun `neighbor actions align with long titles and absent release dates`() {
        compose.setContent {
            AppTheme {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorialAlbumCard(entry, Modifier.width(164.dp), {}, {})
                    EditorialAlbumCard(entry.copy(title = "A very long album title that needs two lines", releaseDate = null),
                        Modifier.width(164.dp), {}, {})
                }
            }
        }
        val first = compose.onNodeWithTag("editorial-find:Album").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("editorial-find:A very long album title that needs two lines").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top)
        assertEquals(first.bottom, second.bottom)
    }

    @Test fun `320dp scaled header controls fit and notes open on demand`() {
        var refreshed = false
        var openedSource = false
        compose.setContent {
            AppTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                    Box(Modifier.width(320.dp)) {
                        EditorialShelfContent(source, feed, 50, true, false, null, {}, { refreshed = true },
                            { openedSource = true }, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText(feed.note).assertDoesNotExist()
        listOf("Refresh AOTY weekly releases", "Open aoty source", "About AOTY weekly releases").forEach { description ->
            val bounds = compose.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 0 && bounds.right <= 320)
        }
        compose.onNodeWithContentDescription("Refresh AOTY weekly releases").performClick()
        compose.onNodeWithContentDescription("Open aoty source").performClick()
        assertTrue(refreshed && openedSource)
        compose.onNodeWithContentDescription("About AOTY weekly releases").performClick()
        compose.onNodeWithText(feed.note).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText(feed.note).assertDoesNotExist()
    }

    @Test fun `zero count is visible and unknown count stays absent`() {
        var found = false
        var reviewed = false
        compose.setContent {
            AppTheme {
                EditorialAlbumCard(entry.copy(scores = EditorialScores(
                    EditorialScore(85.0, 100.0, 0), EditorialScore(70.0, 100.0))),
                    Modifier.width(164.dp), { found = true }, { reviewed = true })
            }
        }
        compose.onNodeWithText("0 reviews").assertIsDisplayed()
        compose.onNodeWithText("0 ratings").assertDoesNotExist()
        compose.onNodeWithText("Find album").performClick()
        compose.onNodeWithContentDescription("Read review for Album").performClick()
        assertTrue(found && reviewed)
    }
}
