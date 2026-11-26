package nl.kwillems.zeilersforum.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.kwillems.zeilersforum.ZfApp
import nl.kwillems.zeilersforum.data.ForumParser
import nl.kwillems.zeilersforum.data.OnlineMember
import nl.kwillems.zeilersforum.data.Post
import nl.kwillems.zeilersforum.data.PostOrder
import nl.kwillems.zeilersforum.data.TopicPage

data class TopicState(
    val title: String = "",
    val posts: List<Post> = emptyList(),
    val order: PostOrder = PostOrder.OLDEST_FIRST,
    val totalPages: Int = 1,
    /** Pagina's die al geladen zijn (1-gebaseerd). */
    val loadedPages: Set<Int> = emptySet(),
    /** Volgende pagina om te laden in de gekozen richting, of null als alles er is. */
    val nextPage: Int? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val replyUrl: String? = null,
    val loggedIn: Boolean = false,
    /** "Wie leest er mee?" (alleen als je bent ingelogd). */
    val readers: List<OnlineMember> = emptyList(),
)

/**
 * Laadt een onderwerp pagina voor pagina. Bij "nieuw → oud" wordt eerst de laatste
 * pagina geladen (berichten omgedraaid) en daarna steeds de pagina ervoor.
 */
class TopicViewModel(
    private val app: ZfApp,
    url: String,
    title: String,
    private val lastPageUrl: String?,
) : ViewModel() {

    private val baseUrl = ForumParser.baseTopicUrl(url)
    private val linkStart = ForumParser.startOf(url)
    private var perPage = 25
    private var job: Job? = null

    private val _state = MutableStateFlow(TopicState(title = title))
    val state: StateFlow<TopicState> = _state

    val settings = app.settings.settings

    init {
        viewModelScope.launch {
            app.settings.settings.map { it.postOrder }.distinctUntilChanged().collect { order ->
                _state.update { it.copy(order = order) }
                load(order, initial = _state.value.posts.isEmpty())
            }
        }
    }

    private fun pageUrl(page: Int) = ForumParser.pageUrl(baseUrl, page, perPage)

    private fun ordered(page: TopicPage, order: PostOrder) =
        if (order == PostOrder.NEWEST_FIRST) page.posts.reversed() else page.posts

    private fun apply(page: TopicPage) {
        perPage = page.perPage
        _state.update {
            it.copy(
                title = page.title.ifBlank { it.title },
                totalPages = page.totalPages,
                replyUrl = page.replyUrl ?: it.replyUrl,
                loggedIn = page.loggedIn,
                readers = page.readers ?: if (page.loggedIn) it.readers else emptyList(),
            )
        }
    }

    /** Laadt vanaf het begin in de gekozen volgorde. [startPage] = specifieke pagina. */
    fun load(order: PostOrder = _state.value.order, initial: Boolean = false, startPage: Int? = null) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            runCatching {
                val first: TopicPage = when {
                    startPage != null -> app.forum.topicPage(pageUrl(startPage))
                    order == PostOrder.OLDEST_FIRST && linkStart > 0 && _state.value.posts.isEmpty() ->
                        app.forum.topicPage("$baseUrl?start=$linkStart")
                    order == PostOrder.OLDEST_FIRST -> app.forum.topicPage(baseUrl)
                    else -> {
                        // Nieuwste eerst: laatste pagina nodig.
                        val hint = lastPageUrl?.substringBefore('#')
                        val candidate = app.forum.topicPage(if (initial && hint != null) hint else baseUrl)
                        if (candidate.page >= candidate.totalPages) candidate
                        else {
                            perPage = candidate.perPage
                            app.forum.topicPage(pageUrl(candidate.totalPages))
                        }
                    }
                }
                first
            }.onSuccess { page ->
                apply(page)
                val next = if (order == PostOrder.NEWEST_FIRST) page.page - 1 else page.page + 1
                _state.update {
                    it.copy(
                        posts = ordered(page, order),
                        loadedPages = setOf(page.page),
                        nextPage = next.takeIf { n -> n in 1..page.totalPages },
                        loading = false,
                        refreshing = false,
                    )
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, refreshing = false, error = e.friendly()) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val next = s.nextPage ?: return
        if (s.loading || s.loadingMore || s.refreshing || next in s.loadedPages) return
        job = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { app.forum.topicPage(pageUrl(next)) }
                .onSuccess { page ->
                    apply(page)
                    val order = _state.value.order
                    val known = _state.value.posts.map { it.id }.toSet()
                    val after = if (order == PostOrder.NEWEST_FIRST) page.page - 1 else page.page + 1
                    _state.update {
                        it.copy(
                            posts = it.posts + ordered(page, order).filter { p -> p.id !in known },
                            loadedPages = it.loadedPages + page.page,
                            nextPage = after.takeIf { n -> n in 1..page.totalPages },
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(loadingMore = false, error = e.friendly()) }
                }
        }
    }

    fun refresh() = load(_state.value.order, initial = false)

    fun goToPage(page: Int) = load(_state.value.order, initial = true, startPage = page)

    fun toggleOrder() {
        viewModelScope.launch {
            val newOrder = if (_state.value.order == PostOrder.OLDEST_FIRST) PostOrder.NEWEST_FIRST else PostOrder.OLDEST_FIRST
            app.settings.setPostOrder(newOrder)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    val webUrl: String get() = baseUrl
}
