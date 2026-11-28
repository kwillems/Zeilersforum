package nl.kwillems.zeilersforum.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.kwillems.zeilersforum.ZfApp
import nl.kwillems.zeilersforum.data.NotLoggedInException
import nl.kwillems.zeilersforum.data.PmBox
import nl.kwillems.zeilersforum.data.PmMessage
import nl.kwillems.zeilersforum.data.PmSummary
import nl.kwillems.zeilersforum.data.PostForm
import nl.kwillems.zeilersforum.ui.components.ErrorPane
import nl.kwillems.zeilersforum.ui.components.PostContent

/** Maximale lengte van een privébericht op de site. */
private const val PM_MAX = 2500

// ---------- Lijst (postvak in / verzonden) ----------

data class PmListState(
    val box: PmBox = PmBox.INBOX,
    val items: List<PmSummary> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val needsLogin: Boolean = false,
    val error: String? = null,
)

class PmListViewModel(private val app: ZfApp) : ViewModel() {
    private val _state = MutableStateFlow(PmListState())
    val state: StateFlow<PmListState> = _state
    private var job: Job? = null

    fun setBox(box: PmBox) {
        if (box == _state.value.box && _state.value.page > 0) return
        _state.value = PmListState(box = box)
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null, needsLogin = false) }
            runCatching { app.forum.pmList(_state.value.box, 1) }
                .onSuccess { p -> _state.update { it.copy(items = p.items, page = p.page, totalPages = p.totalPages, loading = false, refreshing = false) } }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update {
                        it.copy(loading = false, refreshing = false, needsLogin = e is NotLoggedInException,
                            error = if (e is NotLoggedInException) null else e.friendly())
                    }
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.refreshing || s.page == 0 || s.page >= s.totalPages) return
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { app.forum.pmList(s.box, s.page + 1) }
                .onSuccess { p ->
                    _state.update { st ->
                        val known = st.items.map { it.id }.toSet()
                        st.copy(items = st.items + p.items.filter { it.id !in known }, page = p.page, totalPages = p.totalPages, loading = false)
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(loading = false, error = e.friendly()) }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmListContent(
    vm: PmListViewModel,
    loggedIn: Boolean,
    onOpen: (PmSummary, PmBox) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Bij terugkeren (na lezen, beantwoorden of verwijderen) de lijst stil verversen.
    LifecycleResumeEffect(Unit) {
        if (vm.state.value.page == 0) vm.refresh(initial = true) else vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(loggedIn) { if (loggedIn && vm.state.value.needsLogin) vm.refresh(initial = true) }
    val nearEnd by remember {
        derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 10 }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) vm.loadMore() }

    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            listOf(PmBox.INBOX to "Postvak in", PmBox.OUTBOX to "Verzonden").forEachIndexed { i, (box, label) ->
                SegmentedButton(
                    selected = state.box == box,
                    onClick = { vm.setBox(box) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            when {
                state.needsLogin -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AccountCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Log in om je privéberichten te zien.", modifier = Modifier.padding(top = 12.dp))
                    Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp)) { Text("Inloggen") }
                }
                state.items.isEmpty() && state.loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.items.isEmpty() && state.error != null ->
                    ErrorPane(state.error!!, onRetry = { vm.refresh(initial = true) })
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { m -> PmRow(m, state.box, onClick = { onOpen(m, state.box) }) }
                    if (state.loading) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                    }
                    if (!state.loading && state.items.isEmpty()) item {
                        Text("Geen berichten", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PmRow(m: PmSummary, box: PmBox, onClick: () -> Unit) {
    val bold = box == PmBox.INBOX && m.unread
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (bold) MaterialTheme.colorScheme.primary else Color.Transparent))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (box == PmBox.OUTBOX) "Aan " else "") + m.partner,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(m.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    m.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (box == PmBox.OUTBOX && m.unread) Text(
                    "Nog niet gelezen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

// ---------- Eén bericht lezen en beantwoorden ----------

data class PmState(
    val message: PmMessage? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val reply: String = "",
    val sending: Boolean = false,
    val sent: Boolean = false,
    val deleted: Boolean = false,
    val info: String? = null,
)

class PmViewModel(private val app: ZfApp, private val id: String, val box: PmBox) : ViewModel() {
    private val _state = MutableStateFlow(PmState())
    val state: StateFlow<PmState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { app.forum.pmMessage(id, box) }
                .onSuccess { m -> _state.update { it.copy(message = m, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.friendly()) } }
        }
    }

    fun setReply(t: String) = _state.update { it.copy(reply = t.take(PM_MAX)) }

    fun send() {
        val s = _state.value
        val form = s.message?.replyForm ?: return
        if (s.reply.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true) }
            runCatching { app.forum.pmSend(form, s.reply, toName = null, copyToMe = false) }
                .onSuccess { _state.update { it.copy(sending = false, reply = "", sent = true, info = "Antwoord verzonden") } }
                .onFailure { e -> _state.update { it.copy(sending = false, info = e.friendly()) } }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { app.forum.pmDelete(id, box) }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { e -> _state.update { it.copy(info = e.friendly()) } }
        }
    }

    fun clearInfo() = _state.update { it.copy(info = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmScreen(vm: PmViewModel, partner: String, onBack: () -> Unit, onOpenImages: (List<String>, Int) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.info) { state.info?.let { snackbar.showSnackbar(it); vm.clearInfo() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (vm.box == PmBox.INBOX) "Bericht van" else "Bericht aan", style = MaterialTheme.typography.labelMedium)
                        Text(state.message?.partner?.ifBlank { null } ?: partner, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
                actions = {
                    state.message?.partnerUrl?.let { url ->
                        IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }) { Icon(Icons.Filled.Person, "Profiel") }
                    }
                    if (state.message != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Verwijderen") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            val m = state.message
            when {
                state.loading && m == null -> CircularProgressIndicator(Modifier.padding(32.dp))
                m == null -> ErrorPane(state.error ?: "Bericht niet gevonden", onRetry = vm::load)
                else -> Column(
                    Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.avatar != null) AsyncImage(
                            model = m.avatar, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                        ) else Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Person, null) }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(m.partner, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(m.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val images = remember(m) { m.blocks.filterIsInstance<nl.kwillems.zeilersforum.data.Block.Image>().map { it.src } }
                    PostContent(m.blocks, loadImages = true, onImageClick = { src -> onOpenImages(images, images.indexOf(src).coerceAtLeast(0)) })

                    if (m.replyForm != null) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        if (state.sent) Text("✓ Antwoord verzonden", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = state.reply,
                            onValueChange = vm::setReply,
                            label = { Text("Antwoord aan ${m.partner}") },
                            minLines = 4,
                            enabled = !state.sending,
                            supportingText = { Text("${PM_MAX - state.reply.length} tekens over") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = vm::send, enabled = !state.sending && state.reply.isNotBlank()) {
                                if (state.sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Verstuur")
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Bericht verwijderen?") },
        text = { Text(if (vm.box == PmBox.INBOX) "Het bericht gaat naar de prullenbak op de site." else "Het bericht wordt uit je verzonden berichten verwijderd.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Verwijderen") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren") } },
    )
}

// ---------- Nieuw bericht ----------

data class PmComposeState(
    val to: String = "",
    val text: String = "",
    val copyToMe: Boolean = false,
    val form: PostForm? = null,
    val loading: Boolean = true,
    val sending: Boolean = false,
    val needsLogin: Boolean = false,
    val error: String? = null,
    val sent: Boolean = false,
)

class PmComposeViewModel(private val app: ZfApp, to: String?) : ViewModel() {
    private val _state = MutableStateFlow(PmComposeState(to = to.orEmpty()))
    val state: StateFlow<PmComposeState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, needsLogin = false) }
            runCatching { app.forum.pmNewForm() }
                .onSuccess { f -> _state.update { it.copy(form = f, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, needsLogin = e is NotLoggedInException, error = e.friendly()) } }
        }
    }

    fun setTo(v: String) = _state.update { it.copy(to = v) }
    fun setText(v: String) = _state.update { it.copy(text = v.take(PM_MAX)) }
    fun setCopy(v: Boolean) = _state.update { it.copy(copyToMe = v) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun send() {
        val s = _state.value
        val form = s.form ?: return
        if (s.to.isBlank() || s.text.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            runCatching { app.forum.pmSend(form, s.text, toName = s.to, copyToMe = s.copyToMe) }
                .onSuccess { _state.update { it.copy(sending = false, sent = true) } }
                .onFailure { e ->
                    // Een formulier is na een poging verbruikt; een vers formulier ophalen voor de volgende poging.
                    _state.update { it.copy(sending = false, error = e.friendly()) }
                    runCatching { app.forum.pmNewForm() }.onSuccess { f -> _state.update { it.copy(form = f) } }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmComposeScreen(vm: PmComposeViewModel, onBack: () -> Unit, onSent: () -> Unit, onLogin: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.sent) { if (state.sent) onSent() }
    LaunchedEffect(state.error, state.form) {
        if (state.form != null) state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Nieuw bericht") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Annuleren") } },
                actions = {
                    if (state.sending) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp))
                    else IconButton(
                        onClick = vm::send,
                        enabled = state.form != null && state.to.isNotBlank() && state.text.isNotBlank(),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Versturen") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(32.dp))
                state.needsLogin -> Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Je moet ingelogd zijn om berichten te sturen.")
                    Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp)) { Text("Inloggen") }
                }
                state.form == null -> ErrorPane(state.error ?: "Formulier niet gevonden", onRetry = vm::load)
                else -> Column(
                    Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.to, onValueChange = vm::setTo,
                        label = { Text("Aan (gebruikersnaam)") },
                        singleLine = true, enabled = !state.sending,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.text, onValueChange = vm::setText,
                        label = { Text("Bericht") },
                        minLines = 8, enabled = !state.sending,
                        supportingText = { Text("${PM_MAX - state.text.length} tekens over") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.setCopy(!state.copyToMe) }) {
                        Checkbox(checked = state.copyToMe, onCheckedChange = vm::setCopy)
                        Text("Kopie naar mijzelf")
                    }
                }
            }
        }
    }
}
