package nl.kwillems.zeilersforum.ui.screens

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import nl.kwillems.zeilersforum.data.Category
import nl.kwillems.zeilersforum.data.TopicSummary

/** Een forumcategorie: subcategorieën en onderwerpen, met "Nieuw onderwerp". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    vm: TopicListViewModel,
    title: String,
    url: String,
    refreshRequest: StateFlow<Boolean>,
    onRefreshHandled: () -> Unit,
    onBack: () -> Unit,
    onOpenTopic: (TopicSummary) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onNewTopic: (createUrl: String, category: String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val refresh by refreshRequest.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val name = title.ifBlank { state.title }

    LaunchedEffect(refresh) {
        if (refresh) { vm.refresh(); onRefreshHandled() }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
                actions = {
                    IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }) {
                        Icon(Icons.Filled.OpenInBrowser, "Openen in browser")
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            state.createUrl?.let { create ->
                ExtendedFloatingActionButton(
                    onClick = { onNewTopic(create, name) },
                    icon = { Icon(Icons.Filled.Edit, null) },
                    text = { Text("Nieuw onderwerp") },
                )
            }
        },
    ) { padding ->
        TopicListContent(
            vm = vm,
            onOpenTopic = onOpenTopic,
            onOpenCategory = onOpenCategory,
            modifier = Modifier.padding(padding),
        )
    }
}
