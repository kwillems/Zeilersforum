package nl.kwillems.zeilersforum.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.kwillems.zeilersforum.ZfApp
import nl.kwillems.zeilersforum.data.TopicSummary
import nl.kwillems.zeilersforum.ui.components.ErrorPane

data class TopicsState(
    val topics: List<TopicSummary> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class TopicsViewModel(private val app: ZfApp) : ViewModel() {
    private val _state = MutableStateFlow(TopicsState())
    val state: StateFlow<TopicsState> = _state
    val loggedIn = app.forum.loggedIn
    val username = app.forum.username

    init { refresh(initial = true) }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = !initial, loading = initial, error = null) }
            runCatching { app.forum.recentTopics(1) }
                .onSuccess { p -> _state.update { TopicsState(p.topics, p.page, p.totalPages) } }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.friendly()) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.refreshing || s.page >= s.totalPages || s.page == 0) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { app.forum.recentTopics(s.page + 1) }
                .onSuccess { p ->
                    _state.update { st ->
                        val known = st.topics.map { it.id }.toSet()
                        st.copy(topics = st.topics + p.topics.filter { it.id !in known }, page = p.page, totalPages = p.totalPages, loading = false)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.friendly()) } }
        }
    }
}

fun Throwable.friendly(): String = when (this) {
    is java.net.UnknownHostException -> "Geen internetverbinding"
    is java.net.SocketTimeoutException -> "Zeilersforum.nl reageert niet"
    else -> message ?: "Er ging iets mis"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    vm: TopicsViewModel,
    onOpenTopic: (TopicSummary) -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()

    val visible = remember(state.topics, query) {
        if (query.isBlank()) state.topics
        else state.topics.filter {
            it.title.contains(query, true) || it.category.contains(query, true) ||
                it.author.contains(query, true) || it.lastPostBy.contains(query, true)
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, state.topics.size) { if (nearEnd && query.isBlank()) vm.loadMore() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (searching) {
                SearchBarTop(query, { query = it }, onClose = { searching = false; query = "" })
            } else {
                TopAppBar(
                    title = { Text("Zeilersforum", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Zoeken") }
                        IconButton(onClick = onLogin) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                if (loggedIn) "Account" else "Inloggen",
                                tint = if (loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Instellingen") }
                    },
                    scrollBehavior = scroll,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.topics.isEmpty() && state.loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.topics.isEmpty() && state.error != null ->
                    ErrorPane(state.error!!, onRetry = { vm.refresh(initial = true) })
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            if (query.isBlank()) "Recente onderwerpen" else "${visible.size} resultaten",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                    }
                    items(visible, key = { it.id }) { topic ->
                        TopicCard(topic, onClick = { onOpenTopic(topic) }, modifier = Modifier.animateItem())
                    }
                    if (state.loading) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarTop(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Zoek in onderwerpen") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            AnimatedVisibility(query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Wissen") }
            }
        },
    )
}

@Composable
private fun TopicCard(topic: TopicSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    topic.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (topic.hasAttachment) {
                    Icon(
                        Icons.Filled.Attachment, "Bijlage",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(topic.replies.toString())
                }
            }
            Text(
                topic.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                buildString {
                    append("Laatste bericht")
                    if (topic.lastPostBy.isNotBlank()) append(" door ").append(topic.lastPostBy)
                    append(" · ").append(topic.lastPostAgo.ifBlank { topic.lastPostAt })
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).widthIn(max = 900.dp),
            )
        }
    }
}
