package nl.kwillems.zeilersforum.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.kwillems.zeilersforum.data.Block
import nl.kwillems.zeilersforum.data.Post
import nl.kwillems.zeilersforum.data.PostOrder
import nl.kwillems.zeilersforum.data.Settings
import nl.kwillems.zeilersforum.ui.components.ErrorPane
import nl.kwillems.zeilersforum.ui.components.PostContent
import nl.kwillems.zeilersforum.ui.components.ReadersCard
import nl.kwillems.zeilersforum.ui.components.ShowPhotoButton
import nl.kwillems.zeilersforum.ui.components.autoLoadImages
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    vm: TopicViewModel,
    refreshRequest: StateFlow<Boolean>,
    onRefreshHandled: () -> Unit,
    onBack: () -> Unit,
    onOpenImages: (List<String>, Int) -> Unit,
    onReply: (replyUrl: String, title: String, quote: String?) -> Unit,
    onEdit: (editUrl: String, title: String) -> Unit,
    onLogin: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onOpenCategory: (url: String, name: String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val refresh by refreshRequest.collectAsStateWithLifecycle()
    val autoLoad = autoLoadImages(settings.imageMode)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menu by remember { mutableStateOf(false) }
    var pagePicker by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        if (refresh) {
            vm.refresh()
            onRefreshHandled()
            if (state.order == PostOrder.NEWEST_FIRST) listState.scrollToItem(0)
        }
    }
    // Naar een bericht scrollen (opgeslagen leespositie, link met #bericht of na "Naar pagina").
    LaunchedEffect(state.scrollTo, state.posts) {
        val target = state.scrollTo ?: return@LaunchedEffect
        if (state.posts.isEmpty()) return@LaunchedEffect
        val offset = (if (state.readers.isNotEmpty()) 1 else 0) + (if (state.hasEarlier) 1 else 0)
        if (target == TopicState.SCROLL_TOP) listState.scrollToItem(0)
        else state.posts.indexOfFirst { it.id == target }.takeIf { it >= 0 }?.let { listState.scrollToItem(offset + it) }
        vm.clearScroll()
    }
    LaunchedEffect(state.resumed) {
        if (state.resumed) {
            vm.clearResumed()
            // In een eigen coroutine: het wissen van de vlag start dit effect opnieuw, de melding moet blijven.
            scope.launch {
                val r = snackbar.showSnackbar(
                    "Verder waar je was gebleven",
                    actionLabel = if (state.order == PostOrder.NEWEST_FIRST) "Naar het nieuwste" else "Naar het begin",
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (r == SnackbarResult.ActionPerformed) vm.toBeginning()
            }
        }
    }
    // Het bericht bovenaan het scherm onthouden (na even stilstaan, niet bij elke pixel).
    LaunchedEffect(listState) {
        snapshotFlow {
            // Het bovenste bericht in beeld; is daarvan al meer dan de helft voorbij, dan het volgende.
            val info = listState.layoutInfo.visibleItemsInfo
            val first = info.firstOrNull { it.index == listState.firstVisibleItemIndex }
            val pick = if (first != null && listState.firstVisibleItemScrollOffset > first.size / 2)
                info.firstOrNull { it.index == first.index + 1 } ?: first else first
            fun isPost(k: Any?) = (k as? String)?.let { it != "readers" && it != "earlier" } == true
            // Staat de knop "Eerdere berichten" of "Wie leest er mee?" bovenaan, dan het eerste bericht daaronder.
            (if (isPost(pick?.key)) pick else info.firstOrNull { isPost(it.key) })?.key as String?
        }.distinctUntilChanged().collectLatest { id ->
            if (id != null) {
                vm.setTopPost(id)
                delay(800)
                vm.rememberPosition(id)
            }
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); vm.clearNotice() }
    }
    LaunchedEffect(state.error, state.posts.isEmpty()) {
        val err = state.error
        if (err != null && state.posts.isNotEmpty()) {
            snackbar.showSnackbar(err)
            vm.clearError()
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.posts.size) { if (nearEnd) vm.loadMore() }

    val showTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }
    // Nieuw → oud: nieuwere berichten die er bij verversen bovenaan bij komen, in beeld houden.
    nl.kwillems.zeilersforum.ui.components.StickToTop(
        listState, state.posts.firstOrNull()?.id,
        enabled = state.order == PostOrder.NEWEST_FIRST && state.scrollTo == null && !state.loadingPrevious,
        isItem = { it != "readers" && it != "earlier" },
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = {
                    val collapsed = scroll.state.collapsedFraction > 0.5f
                    Column {
                        if (!collapsed) {
                            Text("Zeilersforum", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            if (state.forumName.isNotBlank()) Text(
                                state.forumName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = state.forumUrl?.let { u ->
                                    Modifier.clickable(role = Role.Button) { onOpenCategory(u, state.forumName) }
                                } ?: Modifier,
                            )
                        }
                        Text(
                            state.title,
                            style = if (collapsed) LocalTextStyle.current else MaterialTheme.typography.titleLarge,
                            maxLines = if (collapsed) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.totalPages > 1 && !collapsed) Text(
                            "${state.totalPages} pagina's · ${if (state.order == PostOrder.NEWEST_FIRST) "nieuwste eerst" else "oudste eerst"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                expandedHeight = 184.dp,
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
                actions = {
                    IconButton(onClick = vm::toggleOrder) {
                        Icon(Icons.Filled.SwapVert, if (state.order == PostOrder.NEWEST_FIRST) "Oudste eerst tonen" else "Nieuwste eerst tonen")
                    }
                    if (state.totalPages > 1) IconButton(onClick = { pagePicker = true }) { Icon(Icons.Filled.ViewList, "Naar pagina") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Meer") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Openen in browser") },
                                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) },
                                onClick = { menu = false; ctx.startActivity(Intent(Intent.ACTION_VIEW, vm.webUrl.toUri())) },
                            )
                            DropdownMenuItem(
                                text = { Text("Delen") },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = {
                                    menu = false
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_SUBJECT, state.title)
                                        .putExtra(Intent.EXTRA_TEXT, "${state.title}\n${vm.webUrl}")
                                    ctx.startActivity(Intent.createChooser(send, "Delen"))
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            val reply = state.replyUrl
            when {
                state.loggedIn && reply != null -> ExtendedFloatingActionButton(
                    onClick = { onReply(reply, state.title, null) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                    text = { Text("Reageren") },
                    expanded = !showTop,
                )
                !state.loggedIn && !state.loading -> ExtendedFloatingActionButton(
                    onClick = onLogin,
                    icon = { Icon(Icons.Filled.Login, null) },
                    text = { Text("Inloggen om te reageren") },
                    expanded = !showTop,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading && state.posts.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.posts.isEmpty() && state.error != null ->
                    ErrorPane(state.error!!, onRetry = { vm.load(initial = true) })
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.readers.isNotEmpty()) item(key = "readers") {
                        ReadersCard(state.readers, Modifier.widthIn(max = 960.dp).fillMaxWidth())
                    }
                    if (state.hasEarlier) item(key = "earlier") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (state.loadingPrevious) CircularProgressIndicator(Modifier.padding(8.dp).size(28.dp))
                            else FilledTonalButton(onClick = vm::loadPrevious) {
                                Icon(Icons.Filled.KeyboardArrowUp, null)
                                Text(
                                    (if (state.order == PostOrder.NEWEST_FIRST) "Nieuwere berichten" else "Eerdere berichten") +
                                        " (pagina ${state.pageAbove})",
                                    Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                    items(state.posts, key = { it.id.ifBlank { it.hashCode().toString() } }) { post ->
                        PostCard(
                            post = post,
                            loadImages = autoLoad,
                            canQuote = state.loggedIn && state.replyUrl != null,
                            onQuote = {
                                val quote = "[quote=\"${post.author}\" post=${post.id}]${post.plainText}[/quote]\n"
                                onReply(state.replyUrl!!, state.title, quote)
                            },
                            onEdit = post.editUrl?.let { url -> { onEdit(url, state.title) } },
                            onDelete = post.deleteUrl?.let { { vm.deletePost(post) } },
                            deleting = state.deleting == post.id,
                            onOpenImages = onOpenImages,
                            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
                        )
                    }
                    if (state.loadingMore) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                    if (state.nextPage == null && state.posts.isNotEmpty()) item {
                        Text(
                            if (state.order == PostOrder.NEWEST_FIRST) "Begin van het onderwerp" else "Einde van het onderwerp",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            if (showTop) FilledTonalIconButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) { Icon(Icons.Filled.KeyboardArrowUp, "Naar boven") }
        }
    }

    if (pagePicker) {
        AlertDialog(
            onDismissRequest = { pagePicker = false },
            title = { Text("Naar pagina") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items((1..state.totalPages).toList()) { p ->
                        Text(
                            "Pagina $p" + if (p in state.loadedPages) "  ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pagePicker = false; vm.goToPage(p) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pagePicker = false }) { Text("Sluiten") } },
        )
    }
}

@Composable
private fun PostCard(
    post: Post,
    loadImages: Boolean,
    canQuote: Boolean,
    onQuote: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    deleting: Boolean,
    onOpenImages: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Bericht verwijderen?") },
            text = { Text("Je bericht wordt van het forum gehaald. Dit kun je niet ongedaan maken.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Verwijderen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren") } },
        )
    }
    val inline = remember(post) {
        fun collect(blocks: List<Block>): List<String> = blocks.flatMap {
            when (it) {
                is Block.Image -> listOf(it.src)
                is Block.Quote -> collect(it.blocks)
                else -> emptyList()
            }
        }
        collect(post.blocks)
    }
    val images = remember(post) { (inline + post.attachments.filter { it.isImage }.map { it.url }).distinct() }
    val extra = remember(post) { post.attachments.filter { it.url !in inline } }
    val ctx = LocalContext.current

    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    if (post.avatar != null) {
                        AsyncImage(
                            model = post.avatar, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    } else {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                    if (post.online) Box(
                        Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface).padding(2.dp).clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(0xFF2E7D32)),
                    )
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(post.author, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(post.date, post.dateAgo).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("#${post.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.size(12.dp))
            PostContent(
                blocks = post.blocks,
                loadImages = loadImages,
                onImageClick = { src -> onOpenImages(images, images.indexOf(src).coerceAtLeast(0)) },
            )
            if (extra.isNotEmpty()) {
                Text(
                    "Bijlagen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                val imageAttachments = extra.count { it.isImage }
                var showAttachments by rememberSaveable(post.id) { mutableStateOf(false) }
                if (!loadImages && !showAttachments && imageAttachments > 0) {
                    ShowPhotoButton(
                        src = extra.first { it.isImage }.url,
                        onClick = { showAttachments = true },
                        label = if (imageAttachments == 1) "Bijlage bekijken" else "$imageAttachments bijlagen bekijken",
                    )
                    Spacer(Modifier.size(8.dp))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(extra.filter { !it.isImage || loadImages || showAttachments }) { a ->
                        if (a.isImage) {
                            AsyncImage(
                                model = a.url, contentDescription = a.name, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { onOpenImages(images, images.indexOf(a.url).coerceAtLeast(0)) },
                            )
                        } else {
                            AssistChip(
                                onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, a.url.toUri())) },
                                label = { Text(a.name, maxLines = 1) },
                            )
                        }
                    }
                }
            }
            if (canQuote || onEdit != null || onDelete != null) Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canQuote) TextButton(onClick = onQuote) {
                    Icon(Icons.Filled.FormatQuote, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Citeren")
                }
                if (onEdit != null) TextButton(onClick = onEdit, enabled = !deleting) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bewerken")
                }
                if (deleting) CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(20.dp))
                else if (onDelete != null) TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Verwijderen", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
