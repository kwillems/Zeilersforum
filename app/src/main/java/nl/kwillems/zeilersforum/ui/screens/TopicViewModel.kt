package nl.kwillems.zeilersforum.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    /** Korte melding voor de gebruiker (bijvoorbeeld "Bericht verwijderd"). */
    val notice: String? = null,
    /** Id van het bericht dat nu wordt verwijderd. */
    val deleting: String? = null,
    val replyUrl: String? = null,
    val loggedIn: Boolean = false,
    /** "Wie leest er mee?" (alleen als je bent ingelogd). */
    val readers: List<OnlineMember> = emptyList(),
    /** Forum (categorie) van het onderwerp. */
    val forumName: String = "",
    val forumUrl: String? = null,
    /** Bericht waar de lijst naartoe moet scrollen ([SCROLL_TOP] = bovenaan); daarna weer null. */
    val scrollTo: String? = null,
    /** Net teruggekeerd naar de opgeslagen leespositie: toon de melding met "Naar het begin". */
    val resumed: Boolean = false,
    val loadingPrevious: Boolean = false,
) {
    /** Laagste geladen pagina; bij oud → nieuw kunnen de pagina's daarvoor nog worden geladen. */
    val firstLoadedPage: Int get() = loadedPages.minOrNull() ?: 1
    val lastLoadedPage: Int get() = loadedPages.maxOrNull() ?: 1

    /**
     * Pagina die boven de lijst hoort maar nog niet geladen is: bij oud → nieuw de pagina vóór
     * de eerste geladen pagina, bij nieuw → oud de pagina ná de laatste. null = niets meer.
     */
    val pageAbove: Int? get() = when (order) {
        PostOrder.OLDEST_FIRST -> (firstLoadedPage - 1).takeIf { it >= 1 }
        PostOrder.NEWEST_FIRST -> (lastLoadedPage + 1).takeIf { it <= totalPages }
    }.takeIf { loadedPages.isNotEmpty() }
    val hasEarlier: Boolean get() = pageAbove != null

    companion object {
        const val SCROLL_TOP = "#top"
    }
}

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
    private val topicId = ForumParser.topicId(url)
    /** Berichtnummer uit de link (#1710580), om daar naartoe te springen. */
    private val anchor = url.substringAfter('#', "").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    /** Op welke pagina elk geladen bericht staat, voor het onthouden van de leespositie. */
    private val postPage = mutableMapOf<String, Int>()
    private var firstLoad = true
    /** Bericht dat nu bovenaan het scherm staat (ook als het niet wordt opgeslagen). */
    private var topPost: String? = null
    private var perPage = 25
    private var job: Job? = null

    private val _state = MutableStateFlow(TopicState(title = title))
    val state: StateFlow<TopicState> = _state

    val settings = app.settings.settings

    init {
        viewModelScope.launch {
            app.settings.settings.map { it.postOrder }.distinctUntilChanged().collect { order ->
                _state.update { it.copy(order = order) }
                val first = firstLoad
                firstLoad = false
                // Bij de eerste keer openen (gewone link): verder waar je was gebleven, als dat is ingesteld.
                val mode = app.settings.settings.first().resumeMode
                val resume = if (first && mode.appliesTo(order) && linkStart == 0 && anchor == null)
                    app.readingPositions.get(topicId) else null
                load(
                    order,
                    initial = _state.value.posts.isEmpty(),
                    startPage = resume?.page,
                    scrollTo = resume?.postId ?: anchor.takeIf { first },
                    resuming = resume != null,
                )
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
                forumName = page.forumName.ifBlank { it.forumName },
                forumUrl = page.forumUrl ?: it.forumUrl,
                readers = page.readers ?: if (page.loggedIn) it.readers else emptyList(),
            )
        }
    }

    /**
     * Laadt vanaf het begin in de gekozen volgorde. [startPage] = specifieke pagina,
     * [scrollTo] = bericht (of [TopicState.SCROLL_TOP]) om na het laden naartoe te scrollen.
     */
    fun load(
        order: PostOrder = _state.value.order,
        initial: Boolean = false,
        startPage: Int? = null,
        scrollTo: String? = null,
        resuming: Boolean = false,
    ) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            runCatching {
                val first: TopicPage = when {
                    startPage != null -> app.forum.topicPage(pageUrl(startPage))
                    // Link naar een bepaalde pagina/bericht: die pagina, in beide volgordes.
                    linkStart > 0 && _state.value.posts.isEmpty() ->
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
                remember(page)
                val next = if (order == PostOrder.NEWEST_FIRST) page.page - 1 else page.page + 1
                val target = scrollTo?.takeIf { t -> t == TopicState.SCROLL_TOP || page.posts.any { p -> p.id == t } }
                // Melding alleen als je niet toch al bij het begin (of het nieuwste) zou uitkomen.
                val startOfList = if (order == PostOrder.NEWEST_FIRST) page.page >= page.totalPages else page.page <= 1
                val showResumed = resuming && (!startOfList || (target != null && ordered(page, order).firstOrNull()?.id != target))
                _state.update {
                    it.copy(
                        posts = ordered(page, order),
                        loadedPages = setOf(page.page),
                        nextPage = next.takeIf { n -> n in 1..page.totalPages },
                        loading = false,
                        refreshing = false,
                        scrollTo = target ?: if (startPage != null) TopicState.SCROLL_TOP else null,
                        resumed = showResumed,
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
                    remember(page)
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

    private fun remember(page: TopicPage) = page.posts.forEach { postPage[it.id] = page.page }

    /** Laadt de pagina die boven de lijst hoort (eerdere bij oud → nieuw, nieuwere bij nieuw → oud). */
    fun loadPrevious() {
        val s = _state.value
        val prev = s.pageAbove ?: return
        if (s.loading || s.loadingPrevious || s.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(loadingPrevious = true) }
            runCatching { app.forum.topicPage(pageUrl(prev)) }
                .onSuccess { page ->
                    apply(page)
                    remember(page)
                    _state.update {
                        val known = it.posts.map { p -> p.id }.toSet()
                        it.copy(
                            posts = ordered(page, it.order).filter { p -> p.id !in known } + it.posts,
                            loadedPages = it.loadedPages + page.page,
                            loadingPrevious = false,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(loadingPrevious = false, error = e.friendly()) }
                }
        }
    }

    /** Het bericht bovenaan het scherm; wordt onthouden als leespositie van dit onderwerp. */
    fun setTopPost(postId: String) { topPost = postId }

    fun rememberPosition(postId: String) {
        topPost = postId
        val s = _state.value
        if (s.loading || s.refreshing || s.scrollTo != null) return
        val page = postPage[postId] ?: return
        app.readingPositions.save(topicId, page, postId)
    }

    fun clearScroll() = _state.update { it.copy(scrollTo = null) }
    fun clearResumed() = _state.update { it.copy(resumed = false) }

    /** "Naar het begin" (oud → nieuw) of "Naar het nieuwste" (nieuw → oud): bovenaan de gewone startpagina. */
    fun toBeginning() {
        val order = _state.value.order
        if (order == PostOrder.NEWEST_FIRST) load(order, initial = true, scrollTo = TopicState.SCROLL_TOP)
        else load(order, initial = true, startPage = 1, scrollTo = TopicState.SCROLL_TOP)
    }

    /**
     * Opnieuw laden. Bij oud → nieuw vanaf de pagina waar je bent, en je blijft bij hetzelfde bericht;
     * anders zou je na vernieuwen weer op pagina 1 staan.
     */
    fun refresh() {
        val s = _state.value
        val top = topPost?.takeIf { id -> s.posts.any { it.id == id } }
        if (s.order == PostOrder.OLDEST_FIRST && top != null) {
            load(s.order, initial = false, startPage = postPage[top] ?: s.firstLoadedPage, scrollTo = top)
        } else load(s.order, initial = false)
    }

    fun goToPage(page: Int) = load(_state.value.order, initial = true, startPage = page, scrollTo = TopicState.SCROLL_TOP)

    fun toggleOrder() {
        viewModelScope.launch {
            val newOrder = if (_state.value.order == PostOrder.OLDEST_FIRST) PostOrder.NEWEST_FIRST else PostOrder.OLDEST_FIRST
            app.settings.setPostOrder(newOrder)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    /** Verwijdert een eigen bericht en laadt het onderwerp opnieuw. */
    fun deletePost(post: Post) {
        val url = post.deleteUrl ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = post.id) }
            runCatching { app.forum.deletePost(url) }
                .onSuccess {
                    _state.update { it.copy(deleting = null, notice = "Bericht verwijderd") }
                    refresh()
                }
                .onFailure { e -> _state.update { it.copy(deleting = null, error = e.friendly()) } }
        }
    }

    val webUrl: String get() = baseUrl
}
