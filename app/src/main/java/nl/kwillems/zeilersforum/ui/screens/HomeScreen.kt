package nl.kwillems.zeilersforum.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.kwillems.zeilersforum.ZfApp
import nl.kwillems.zeilersforum.data.Category
import nl.kwillems.zeilersforum.data.PmBox
import nl.kwillems.zeilersforum.data.PmSummary
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import nl.kwillems.zeilersforum.data.ForumRepository
import nl.kwillems.zeilersforum.data.SearchResult
import nl.kwillems.zeilersforum.data.Section
import nl.kwillems.zeilersforum.data.TopicSummary
import nl.kwillems.zeilersforum.ui.components.ErrorPane
import nl.kwillems.zeilersforum.ui.components.HtmlText

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    RECENT("Recent", Icons.Outlined.Schedule, Icons.Filled.Schedule),
    FORUM("Forum", Icons.Outlined.Forum, Icons.Filled.Forum),
    SEARCH("Zoeken", Icons.Filled.Search, Icons.Filled.Search),
    MINE("Mijn", Icons.Outlined.Person, Icons.Filled.Person),
    MESSAGES("Berichten", Icons.Outlined.Mail, Icons.Filled.Mail),
}

/** Startscherm met tabbladen; op een tablet wordt de onderbalk automatisch een zijbalk. */
@Composable
fun HomeScreen(
    app: ZfApp,
    onOpenTopic: (url: String, title: String, lastPageUrl: String?) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    onOpenMessage: (PmSummary, PmBox) -> Unit,
    onComposeMessage: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val loggedIn by app.forum.loggedIn.collectAsStateWithLifecycle()
    val pmUnread by app.forum.pmUnread.collectAsStateWithLifecycle()
    val openTopic: (TopicSummary) -> Unit = { onOpenTopic(it.url, it.title, it.lastPostUrl) }

    // ViewModels worden pas aangemaakt (en laden pas) als het tabblad voor het eerst wordt geopend.
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEachIndexed { i, t ->
                item(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = {
                        val unread = if (t == Tab.MESSAGES) pmUnread ?: 0 else 0
                        BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toString()) } }) {
                            Icon(if (tab == i) t.selectedIcon else t.icon, null)
                        }
                    },
                    label = { Text(t.label) },
                )
            }
        },
    ) {
        val actions: @Composable () -> Unit = {
            IconButton(onClick = onLogin) {
                Icon(
                    Icons.Filled.AccountCircle, if (loggedIn) "Account" else "Inloggen",
                    tint = if (loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Instellingen") }
        }
        when (Tab.entries[tab]) {
            Tab.RECENT -> TabScaffold("Zeilersforum", actions) { mod ->
                val recentVm = viewModel(key = "recent") { TopicListViewModel(app, ForumRepository.RECENT_URL) }
                TopicListContent(recentVm, onOpenTopic = openTopic, modifier = mod, header = {
                    item { SectionLabel("Recente discussies") }
                })
            }
            Tab.FORUM -> TabScaffold("Forum", actions) { mod ->
                val indexVm = viewModel(key = "index") { ForumIndexViewModel(app) }
                ForumIndexContent(indexVm, onOpenCategory, mod)
            }
            Tab.SEARCH -> TabScaffold("Zoeken", actions) { mod ->
                val searchVm = viewModel(key = "search") { SearchViewModel(app) }
                SearchContent(searchVm, onOpen = { r -> onOpenTopic(r.url, r.title, null) }, modifier = mod)
            }
            Tab.MESSAGES -> TabScaffold(
                "Privéberichten", actions,
                fab = {
                    if (loggedIn) ExtendedFloatingActionButton(
                        onClick = onComposeMessage,
                        icon = { Icon(Icons.Filled.Edit, null) },
                        text = { Text("Nieuw bericht") },
                    )
                },
            ) { mod ->
                val pmVm = viewModel(key = "pm") { PmListViewModel(app) }
                PmListContent(pmVm, loggedIn, onOpen = onOpenMessage, onLogin = onLogin, modifier = mod)
            }
            Tab.MINE -> TabScaffold("Mijn onderwerpen", actions) { mod ->
                MyTopicsContent(app, loggedIn, openTopic, onLogin, mod)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabScaffold(
    title: String,
    actions: @Composable () -> Unit,
    fab: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        floatingActionButton = fab,
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                actions = { actions() },
                scrollBehavior = scroll,
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

// ---------- Forum (categorieën) ----------

data class IndexState(val sections: List<Section> = emptyList(), val loading: Boolean = true, val refreshing: Boolean = false, val error: String? = null)

class ForumIndexViewModel(private val app: ZfApp) : ViewModel() {
    private val _state = MutableStateFlow(IndexState())
    val state: StateFlow<IndexState> = _state

    init { load(initial = true) }

    fun load(initial: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            runCatching { app.forum.forumIndex() }
                .onSuccess { s -> _state.value = IndexState(s, loading = false) }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.friendly()) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumIndexContent(vm: ForumIndexViewModel, onOpenCategory: (Category) -> Unit, modifier: Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load() }, modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.sections.isEmpty() && state.error != null -> ErrorPane(state.error!!, onRetry = { vm.load(initial = true) })
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.sections.forEach { section ->
                    item(key = "s" + section.url) { SectionLabel(section.name) }
                    items(section.categories, key = { it.url }) { c -> CategoryCard(c, onClick = { onOpenCategory(c) }) }
                }
            }
        }
    }
}

// ---------- Zoeken ----------

data class SearchState(
    val query: String = "",
    val titleOnly: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val total: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false,
)

class SearchViewModel(private val app: ZfApp) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setTitleOnly(v: Boolean) {
        _state.update { it.copy(titleOnly = v) }
        if (_state.value.searched) search()
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.length < 3) {
            _state.update { it.copy(error = "Typ minstens 3 tekens") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, results = emptyList(), page = 0, searched = true) }
            runCatching { app.forum.search(q, _state.value.titleOnly, "", 1) }
                .onSuccess { p -> _state.update { it.copy(results = p.results, page = p.page, totalPages = p.totalPages, total = p.total, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.friendly()) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.page == 0 || s.page >= s.totalPages) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { app.forum.search(s.query.trim(), s.titleOnly, "", s.page + 1) }
                .onSuccess { p -> _state.update { it.copy(results = it.results + p.results, page = p.page, totalPages = p.totalPages, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.friendly()) } }
        }
    }
}

@Composable
private fun SearchContent(vm: SearchViewModel, onOpen: (SearchResult) -> Unit, modifier: Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, state.results.size) { if (nearEnd) vm.loadMore() }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Zoek in het hele forum") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Wissen") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !state.titleOnly, onClick = { vm.setTitleOnly(false) }, label = { Text("Hele bericht") })
            FilterChip(selected = state.titleOnly, onClick = { vm.setTitleOnly(true) }, label = { Text("Alleen titels") })
            if (state.searched && !state.loading) Text(
                "${state.total} resultaten", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline,
            )
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.results, key = { it.url }) { r -> SearchResultCard(r, onClick = { onOpen(r) }) }
            if (state.loading) item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            if (state.searched && !state.loading && state.results.isEmpty() && state.error == null) item {
                Text("Niets gevonden", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun SearchResultCard(r: SearchResult, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.avatar != null) AsyncImage(
                    model = r.avatar, contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape),
                )
                Text(
                    "${r.author} · ${r.dateAgo.ifBlank { r.date }}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(r.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            if (r.category.isNotBlank()) Text(r.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (r.snippetHtml.isNotBlank()) Box(Modifier.padding(top = 6.dp)) {
                HtmlText(r.snippetHtml, maxLines = 5)
            }
        }
    }
}

// ---------- Mijn onderwerpen ----------

@Composable
private fun MyTopicsContent(
    app: ZfApp,
    loggedIn: Boolean,
    onOpenTopic: (TopicSummary) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val urls = listOf(ForumRepository.MY_POSTED_URL, ForumRepository.MY_STARTED_URL)
    val vm = viewModel(key = "mine") { TopicListViewModel(app, urls[mode], needsLogin = true) }
    // Houd de getoonde lijst gelijk aan de gekozen knop (ook na terugkeren naar dit tabblad).
    LaunchedEffect(mode) { vm.switchTo(urls[mode]) }
    LaunchedEffect(loggedIn) { if (loggedIn && vm.state.value.needsLogin) vm.refresh(initial = true) }

    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            listOf("Meegedaan", "Zelf gestart").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i,
                    onClick = { mode = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        TopicListContent(
            vm = vm,
            onOpenTopic = onOpenTopic,
            emptyText = "Nog geen onderwerpen",
            loginPrompt = {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AccountCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Log in om je eigen onderwerpen te zien.", modifier = Modifier.padding(top = 12.dp))
                    Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp)) { Text("Inloggen") }
                }
            },
        )
    }
}

