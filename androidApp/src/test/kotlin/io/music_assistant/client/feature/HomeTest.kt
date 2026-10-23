package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.assertFloatingBar
import io.music_assistant.client.support.pages.assertMediaDisplayed
import io.music_assistant.client.support.pages.assertMediaNotDisplayed
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class HomeTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    private val legacyScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun cancelLegacyScope() {
        legacyScope.cancel()
    }

    // The default FakeServiceClient serves item-less rows resolved per row;
    // legacy versions embed the items in the rows response.
    @Test
    fun `loads home recommendations from servers that embed row items`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(album)

        // The shared fake no longer has a constructible legacy version. Adapt its
        // wire responses locally, keeping the per-row command unavailable.
        val legacyClient = EmbeddedRecommendationClient(serviceClient, legacyScope)
        loadKoinModules(module { single<ServiceClient> { legacyClient } })

        launchLoggedInApp(composeTestRule, serviceClient)
            .assertRecommendationDisplayed(album)
    }

    @Test
    fun `can refresh home recommendations`() {
        val album1 = ServerMediaItemFixtures.album()
        serviceClient.addItems(album1)

        val homePage = launchLoggedInApp(composeTestRule, serviceClient)
            .assertRecommendationDisplayed(album1)

        val album2 = ServerMediaItemFixtures.album()
        serviceClient.addItems(album2)

        homePage.refresh()
            .assertRecommendationDisplayed(album2)
    }

    @Test
    fun `can refresh home shortcuts`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(album)

        val homePage = launchLoggedInApp(composeTestRule, serviceClient)

        serviceClient.addShortcut(album)

        homePage.refresh()
            .assertShortcutDisplayed(album)
    }

    @Test
    fun `shows error if data can't be loaded and can recover with refresh`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(album)
        val homePage = launchLoggedInApp(composeTestRule, serviceClient)
            .assertRecommendationDisplayed(album)

        serviceClient.setRequestErrors(true)
        homePage.refresh()
            .assertErrorLoadingData()
            .assertMediaNotDisplayed(album)

        serviceClient.setRequestErrors(false)
        homePage.refresh()
            .assertRecommendationDisplayed(album)
    }

    @Test
    fun `edit mode hides the floating player bar`() {
        serviceClient.addItems(ServerMediaItemFixtures.album())

        launchLoggedInApp(composeTestRule, serviceClient)
            .assertFloatingBar(showing = true)
            .editRows()
            .assertFloatingBar(showing = false)
            .saveRows()
            .assertFloatingBar(showing = true)
    }
}

private class EmbeddedRecommendationClient(
    private val delegate: FakeServiceClient,
    scope: CoroutineScope,
) : ServiceClient by delegate {
    override val sessionState = delegate.sessionState.map { state ->
        when (state) {
            is SessionState.Connected.Direct -> state.copy(
                connectionData = state.connectionData.copy(
                    serverInfo = state.connectionData.serverInfo?.copy(schemaVersion = -1),
                ),
            )
            else -> state
        }
    }.stateIn(scope, SharingStarted.Eagerly, delegate.sessionState.value)

    override suspend fun sendRequest(request: Request): Result<Answer> = when (request.command) {
        APICommands.MUSIC_RECOMMENDATIONS -> delegate.sendRequest(request).map { answer ->
            val rows = checkNotNull(answer.resultAs<List<ServerMediaItem>>()).map { row ->
                val itemsAnswer = delegate.sendRequest(
                    Request.Library.recommendationItems(row.provider, row.itemId),
                ).getOrThrow()
                row.copy(items = checkNotNull(itemsAnswer.resultAs<List<ServerMediaItem>>()))
            }
            answer.copy(json = JsonObject(answer.json + ("result" to myJson.encodeToJsonElement(rows))))
        }
        APICommands.MUSIC_RECOMMENDATIONS_ITEMS -> Result.failure(UnsupportedOperationException())
        else -> delegate.sendRequest(request)
    }
}
