package nl.kwillems.zeilersforum.ui.screens

import nl.kwillems.zeilersforum.ui.components.prefetchOnHold
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.kwillems.zeilersforum.ZfApp
import nl.kwillems.zeilersforum.data.Category
import nl.kwillems.zeilersforum.data.NotLoggedInException
import nl.kwillems.zeilersforum.data.TopicSummary
import nl.kwillems.zeilersforum.ui.components.ErrorPane

data class TopicListState(
    val title: String = "",
    val topics: List<TopicSummary> = emptyList(),
    val subcategories: List<Category> = emptyList(),
    val createUrl: String? = null,
    val page: Int = 0,
    val totalPages: Int = 1,
    val perPage: Int = 40,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val needsLogin: Boolean = false,
    val error: String? = null,
)

/** Een lijst met onderwerpen die pagina voor pagina geladen wordt (recent, categorie, mijn onderwerpen). */
class TopicListViewModel(
    private val app: ZfApp,
    private var baseUrl: String,
    private val needsLogin: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(TopicListState())
    val state: StateFlow<TopicListState> = _state
    private var job: Job? = null

    init { refresh(initial = true) }

    /** Andere lijst tonen (bijvoorbeeld "Gestart" ↔ "Deelgenomen"). */
    fun switchTo(url: String) {
        if (url == baseUrl) return
        baseUrl = url
        _state.value = TopicListState()
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(refreshing = !initial, loading = initial, error = null, needsLogin = false) }
            // Eerst de bewaarde versie tonen (meteen), dan de nieuwe ophalen.
            if (initial && _state.value.topics.isEmpty()) {
                app.forum.cachedTopicList(baseUrl, 1, _state.value.perPage)?.let { p ->
                    _state.update {
                        TopicListState(
                            title = p.title, topics = p.topics, subcategories = p.subcategories, createUrl = p.createUrl,
                            page = p.page, totalPages = p.totalPages, perPage = p.perPage, refreshing = true,
                        )
                    }
                }
            }
            runCatching {
                // Bij het opstarten heeft de app de Recente discussies misschien al opgehaald.
                val pre = if (initial) app.takeRecentPrefetch(baseUrl) else null
                pre?.let { d -> runCatching { d.await() }.getOrNull() }
                    ?: app.forum.topicList(baseUrl, 1, _state.value.perPage, needsLogin)
            }
                .onSuccess { p ->
                    _state.update {
                        TopicListState(
                            title = p.title, topics = p.topics, subcategories = p.subcategories, createUrl = p.createUrl,
                            page = p.page, totalPages = p.totalPages, perPage = p.perPage,
                        )
                    }
                }
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
        if (s.loading || s.refreshing || s.page >= s.totalPages || s.page == 0) return
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { app.forum.topicList(baseUrl, s.page + 1, s.perPage, needsLogin) }
                .onSuccess { p ->
                    _state.update { st ->
                        val known = st.topics.map { it.id }.toSet()
                        st.copy(topics = st.topics + p.topics.filter { it.id !in known }, page = p.page, totalPages = p.totalPages, loading = false)
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(loading = false, error = e.friendly()) }
                }
        }
    }
}

fun Throwable.friendly(): String = when (this) {
    is java.net.UnknownHostException -> "Geen internetverbinding"
    is java.net.SocketTimeoutException -> "Zeilersforum.nl reageert niet"
    is NotLoggedInException -> "Je moet ingelogd zijn"
    else -> message ?: "Er ging iets mis"
}

/** De lijst zelf, met verversen door omlaag te trekken en automatisch verder laden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicListContent(
    vm: TopicListViewModel,
    onOpenTopic: (TopicSummary) -> Unit,
    onOpenCategory: (Category) -> Unit = {},
    modifier: Modifier = Modifier,
    emptyText: String = "Geen onderwerpen",
    loginPrompt: @Composable () -> Unit = {},
    header: LazyListScope.() -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Nieuwe onderwerpen bovenaan na verversen (ook na het tonen van de bewaarde versie) in beeld houden.
    nl.kwillems.zeilersforum.ui.components.StickToTop(listState, state.topics.firstOrNull()?.id, isItem = { (it as? String)?.startsWith("c") != true })
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Ruim van tevoren (pagina = 40 onderwerpen).
            last >= listState.layoutInfo.totalItemsCount - 15
        }
    }
    LaunchedEffect(nearEnd, state.topics.size, state.refreshing, state.loading) { if (nearEnd) vm.loadMore() }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = modifier.fillMaxSize()) {
        when {
            state.needsLogin -> loginPrompt()
            state.topics.isEmpty() && state.subcategories.isEmpty() && state.loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.topics.isEmpty() && state.subcategories.isEmpty() && state.error != null ->
                ErrorPane(state.error!!, onRetry = { vm.refresh(initial = true) })
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                header()
                if (state.subcategories.isNotEmpty()) {
                    items(state.subcategories, key = { "c" + it.url }) { c -> CategoryCard(c, onClick = { onOpenCategory(c) }) }
                    if (state.topics.isNotEmpty()) item { SectionLabel("Onderwerpen") }
                }
                items(state.topics, key = { it.id }) { topic ->
                    TopicCard(topic, onClick = { onOpenTopic(topic) }, modifier = Modifier.animateItem())
                }
                if (state.loading) item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
                if (!state.loading && state.topics.isEmpty() && state.subcategories.isEmpty()) item {
                    Text(emptyText, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun TopicCard(topic: TopicSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as ZfApp
    val prefetch = { app.prefetchTopic(topic.url, topic.lastPostUrl) }
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
            .prefetchOnHold(topic.url, prefetch)
            .clickable(onClick = { prefetch(); onClick() }),
    ) {
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
                if (topic.newCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Text("${topic.newCount} nieuw")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (topic.hasAttachment) {
                    Icon(Icons.Filled.Attachment, "Bijlage", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun CategoryCard(c: Category, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(c.name, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Column {
                    if (c.description.isNotBlank()) Text(c.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val stats = listOfNotNull(
                        c.topics.takeIf { it.isNotBlank() }?.let { "$it onderwerpen" },
                        c.lastAgo.takeIf { it.isNotBlank() }?.let { "laatste $it" },
                    ).joinToString(" · ")
                    if (stats.isNotBlank()) Text(stats, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    if (c.children.isNotEmpty()) Text(
                        "Met: " + c.children.joinToString { it.first },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        )
    }
}
