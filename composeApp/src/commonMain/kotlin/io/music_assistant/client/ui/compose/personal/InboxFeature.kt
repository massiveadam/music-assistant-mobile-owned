@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, kotlin.uuid.ExperimentalUuidApi::class)

package io.music_assistant.client.ui.compose.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.items.image
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.PersonalApi
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.server.PersonalItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private val inboxJson = Json { ignoreUnknownKeys = false }

@Serializable
internal data class InboxUser(@SerialName("user_id") val userId: String, val name: String)
@Serializable
internal data class InboxUsers(@SerialName("user_id") val userId: String, val users: List<InboxUser>)
@Serializable
data class SharedAlbum(val name: String, val artists: List<String>)
@Serializable
data class InboxMessage(
    val id: String,
    @SerialName("sender_name") val senderName: String,
    val album: SharedAlbum,
    val note: String,
    @SerialName("sent_at") val sentAt: Double,
    @SerialName("read_at") val readAt: Double?,
)
@Serializable
data class InboxSnapshot(
    @SerialName("user_id") val userId: String,
    val messages: List<InboxMessage>,
    val unread: Int,
)
@Serializable
internal data class InboxSent(@SerialName("user_id") val userId: String, @SerialName("sent_id") val sentId: String)
@Serializable
internal data class InboxOpened(@SerialName("user_id") val userId: String, val item: PersonalItem?)

fun decodeInboxSnapshot(value: JsonObject, userId: String): InboxSnapshot =
    inboxJson.decodeFromJsonElement<InboxSnapshot>(value).also { snapshot ->
        require(snapshot.userId == userId && snapshot.unread >= 0 && snapshot.messages.size <= 200)
        require(snapshot.messages.map { it.id }.distinct().size == snapshot.messages.size)
        require(snapshot.messages.all { it.id.isNotBlank() && it.album.name.isNotBlank() &&
            it.note.length <= 500 && it.sentAt.isFinite() && it.sentAt >= 0 &&
            (it.readAt == null || (it.readAt.isFinite() && it.readAt >= 0)) })
    }

private suspend fun loadInbox(api: PersonalApi, session: PersonalApi.Session): InboxSnapshot =
    decodeInboxSnapshot(api.request("/credits/v1/inbox", session = session), session.userId)

private fun inboxAction(action: String, messageId: String): JsonObject = buildJsonObject {
    put("action", action)
    put("message_id", messageId)
}

/** A session object changes only when its authenticated identity or connection changes. */
@Composable
private fun rememberInboxSession(api: PersonalApi): PersonalApi.Session? {
    val connection by api.serviceClient.sessionState.collectAsStateWithLifecycle()
    var remembered by remember(api) { mutableStateOf(api.session()) }
    val current = remember(connection) { api.session() }
    SideEffect { if (remembered?.same(current) != true && !(remembered == null && current == null)) remembered = current }
    // Do not render or accept actions for the old identity while SideEffect is pending.
    return remembered?.takeIf { it.same(current) && it.same(api.session()) }
}

private val inboxRefreshVersion = MutableStateFlow(0)
private fun notifyInboxChanged() = inboxRefreshVersion.update { it + 1 }

/** Single observer in the navigation root, canceled on account change or backgrounding. */
@Composable
fun rememberInboxUnreadCount(): Int {
    val api = koinInject<PersonalApi>()
    val session = rememberInboxSession(api)
    val refreshVersion by inboxRefreshVersion.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var count by remember(session) { mutableIntStateOf(0) }
    LaunchedEffect(session, refreshVersion, lifecycle) {
        if (session == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                try { count = loadInbox(api, session).unread }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Retain the last valid count for this account. */ }
                delay(30000)
            }
        }
    }
    return count
}

@Composable
fun InboxUnreadBadge(count: Int) {
    if (count > 0) Badge(Modifier.semantics { contentDescription = "$count unread albums" }) {
        Text(if (count > 99) "99+" else count.toString())
    }
}

@Composable
fun InboxShortcut(unread: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("Inbox")
        if (unread > 0) {
            Spacer(Modifier.width(6.dp))
            InboxUnreadBadge(unread)
        }
    }
}

@Composable
fun SendAlbumButton(album: Album, modifier: Modifier = Modifier) {
    val api = koinInject<PersonalApi>()
    val session = rememberInboxSession(api)
    key(session, album.mediaUri) {
        var showing by remember { mutableStateOf(false) }
        OutlinedButton(
            enabled = session != null && !album.mediaUri.isNullOrBlank(),
            onClick = { showing = true },
            modifier = modifier.heightIn(min = 48.dp).testTag("SendAlbumButton"),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        ) { Text("Send album") }
        if (showing && session != null) SendAlbumDialog(album, api, session) { showing = false }
    }
}

@Composable
private fun SendAlbumDialog(album: Album, api: PersonalApi, session: PersonalApi.Session, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<InboxUser>>(emptyList()) }
    var recipient by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf(Uuid.random().toString()) }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(session, reload) {
        loading = true
        error = null
        try {
            val result = inboxJson.decodeFromJsonElement<InboxUsers>(api.request("/credits/v1/inbox-users", session = session))
            api.requireCurrent(session)
            require(result.userId == session.userId && result.users.all { it.userId.isNotBlank() && it.name.isNotBlank() && it.userId != session.userId })
            require(result.users.map { it.userId }.distinct().size == result.users.size)
            users = result.users
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (session.same(api.session())) error = failure.message ?: "Could not load recipients." }
        finally { if (session.same(api.session())) loading = false }
    }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(if (sent) "Album sent" else "Send album") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(album.name, style = MaterialTheme.typography.titleMedium)
                if (sent) Text("Your album is now in their inbox.") else {
                    Text("Choose a recipient")
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(users, key = { it.userId }) { user ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !sending) {
                                if (recipient != user.userId) { recipient = user.userId; requestId = Uuid.random().toString(); error = null }
                            }) {
                                RadioButton(selected = recipient == user.userId, enabled = !sending, onClick = {
                                    if (recipient != user.userId) { recipient = user.userId; requestId = Uuid.random().toString(); error = null }
                                })
                                Text(user.name, Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                    if (!loading && users.isEmpty() && error == null) Text("There are no other enabled accounts on this server.")
                    OutlinedTextField(note, { next ->
                        if (next.length <= 500 && next != note) { note = next; requestId = Uuid.random().toString(); error = null }
                    }, enabled = !sending, label = { Text("Optional note") },
                        supportingText = { Text("${note.length}/500") }, minLines = 2, maxLines = 4,
                        modifier = Modifier.fillMaxWidth())
                    if (session.origin == null) Text("Connect using the Sanchez HTTPS address to send an album.")
                    if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!loading && !sending && users.isEmpty() && error != null) {
                    TextButton(onClick = { reload++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry recipients") }
                }
            }
        },
        confirmButton = {
            if (sent) TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
            else TextButton(enabled = !loading && !sending && recipient != null && session.origin != null, onClick = {
                val target = recipient ?: return@TextButton
                val uri = album.mediaUri ?: return@TextButton
                scope.launch {
                    sending = true
                    error = null
                    try {
                        val result = inboxJson.decodeFromJsonElement<InboxSent>(api.request("/credits/v1/inbox", buildJsonObject {
                            put("action", "send"); put("recipient_id", target); put("uri", uri)
                            put("note", note); put("request_id", requestId)
                        }, session))
                        api.requireCurrent(session)
                        require(result.userId == session.userId && result.sentId.isNotBlank())
                        sent = true
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { if (session.same(api.session())) error = failure.message ?: "Could not send album. Retry safely." }
                    finally { if (session.same(api.session())) sending = false }
                }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (sending) "Sending…" else "Send") }
        },
        dismissButton = { if (!sent) TextButton(enabled = !sending, onClick = onDismiss,
            modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

@Composable
fun AlbumInboxScreen(contentPadding: PaddingValues, onBack: () -> Unit, onNavigateClick: (AppMediaItem) -> Unit,
    onFindAlbum: (String) -> Unit = {},
) {
    val api = koinInject<PersonalApi>()
    val factory = koinInject<MediaItemFactory>()
    val session = rememberInboxSession(api)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    key(session) {
        if (session == null) {
            Column(Modifier.fillMaxSize()) {
                InboxTopBar("Inbox", onBack, {}, false)
                Text("Sign in to view your inbox.", Modifier.padding(16.dp))
            }
        } else InboxContent(api, factory, session, contentPadding, onBack, onNavigateClick, onFindAlbum)
    }
    }
}

@Composable
private fun InboxContent(api: PersonalApi, factory: MediaItemFactory, session: PersonalApi.Session,
    contentPadding: PaddingValues, onBack: () -> Unit, onNavigateClick: (AppMediaItem) -> Unit,
    onFindAlbum: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<InboxSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val refreshVersion by inboxRefreshVersion.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(session, reload, refreshVersion, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                loading = snapshot == null
                error = null
                try { snapshot = loadInbox(api, session) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { if (session.same(api.session())) error = failure.message ?: "Could not load inbox." }
                finally { if (session.same(api.session())) loading = false }
                delay(30000)
            }
        }
    }
    fun action(kind: String, message: InboxMessage) {
        if (busy || loading || !session.same(api.session())) return
        scope.launch {
            busy = true
            error = null
            notice = null
            try {
                if (kind == "open") {
                    val opened = inboxJson.decodeFromJsonElement<InboxOpened>(api.request("/credits/v1/inbox", inboxAction("open", message.id), session))
                    api.requireCurrent(session)
                    require(opened.userId == session.userId)
                    val item = opened.item
                    require(item == null || (item.mediaType == "album" && item.uri.isNotBlank() && item.available))
                    val album = item?.let { factory.create(it.asServer()) as? Album }
                    check(item == null || album != null) { "Invalid album response." }
                    // Accessible albums are marked read when opened. Unavailable recommendations stay unread.
                    if (album != null) {
                        snapshot = decodeInboxSnapshot(api.request("/credits/v1/inbox", inboxAction("read", message.id), session), session.userId)
                        api.requireCurrent(session)
                        notifyInboxChanged()
                        onNavigateClick(album)
                    } else notice = "This album is unavailable through your music sources. Search your catalog for ${message.album.name}."
                } else {
                    snapshot = decodeInboxSnapshot(api.request("/credits/v1/inbox", inboxAction(kind, message.id), session), session.userId)
                    notifyInboxChanged()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (session.same(api.session())) error = failure.message ?: "Could not update inbox." }
            finally { if (session.same(api.session())) busy = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        InboxTopBar("Inbox${snapshot?.unread?.takeIf { it > 0 }?.let { " ($it unread)" }.orEmpty()}", onBack,
            { reload++ }, !loading && !busy)
        if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        notice?.let { Text(it, modifier = Modifier.padding(16.dp)) }
        if (session.origin == null) Text("Connect using the Sanchez HTTPS address to open, mark read, or archive recommendations.", Modifier.padding(16.dp))
        if (!loading && snapshot?.messages?.isEmpty() == true) Text("Albums sent to you will appear here.", Modifier.padding(16.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("AlbumInboxList"), contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(snapshot?.messages.orEmpty(), key = { it.id }) { message ->
                var artwork by remember(session, message.id) { mutableStateOf<String?>(null) }
                // Resolve only visible cards using the recipient's existing permission check.
                // The open operation is read-only; marking read remains an explicit action.
                LaunchedEffect(session, message.id) {
                    if (session.origin == null) return@LaunchedEffect
                    try {
                        val result = inboxJson.decodeFromJsonElement<InboxOpened>(api.request(
                            "/credits/v1/inbox", inboxAction("open", message.id), session))
                        api.requireCurrent(session)
                        require(result.userId == session.userId)
                        val item = result.item?.takeIf { it.mediaType == "album" && it.available }
                        artwork = item?.let { factory.create(it.asServer())?.image(ImageType.THUMB)?.url }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Album initial remains available offline. */ }
                }
                InboxMessageCard(message, artwork, !loading && !busy && session.origin != null,
                    !loading && !busy && session.same(api.session()),
                    onOpen = { action("open", message) }, onRead = { action("read", message) },
                    onArchive = { action("archive", message) }, onFind = {
                        onFindAlbum((message.album.artists + message.album.name).joinToString(" "))
                    })
            }
        }
    }
}

@Composable
private fun InboxTopBar(title: String, onBack: () -> Unit, onRefresh: () -> Unit, enabled: Boolean) {
    TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
        IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }
    }, actions = { IconButton(enabled = enabled, onClick = onRefresh, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Refresh, "Refresh inbox") } })
}


/** Compact inbox rendering, separate from account-scoped loading and mutations. */
@Composable
fun InboxMessageCard(message: InboxMessage, artwork: String?, enabled: Boolean, canFind: Boolean,
    onOpen: () -> Unit, onRead: () -> Unit, onArchive: () -> Unit, onFind: () -> Unit,
) {
    var menuOpen by remember(message.id) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("InboxMessage-${message.id}"),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(message.album.name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall,
                        color = muted)
                    if (artwork != null) AsyncImage(model = artwork, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("From ${message.senderName}", style = MaterialTheme.typography.labelMedium,
                        color = if (message.readAt == null) MaterialTheme.colorScheme.primary else muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(message.album.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (message.album.artists.isNotEmpty()) Text(message.album.artists.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = muted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (message.note.isNotEmpty()) Text(message.note, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (message.readAt == null) "Unread" else "Read",
                    style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
                FilledTonalButton(enabled = enabled, onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Open album", style = MaterialTheme.typography.labelLarge)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = enabled || canFind,
                        modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, "More actions for ${message.album.name}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Find album") }, enabled = canFind,
                            onClick = { menuOpen = false; onFind() })
                        if (message.readAt == null) DropdownMenuItem(text = { Text("Mark read") }, enabled = enabled,
                            onClick = { menuOpen = false; onRead() })
                        DropdownMenuItem(text = { Text("Archive") }, enabled = enabled,
                            onClick = { menuOpen = false; onArchive() })
                    }
                }
            }
        }
    }
}
