package nl.kwillems.zeilersforum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Alle communicatie met zeilersforum.nl. */
class ForumRepository(
    private val client: OkHttpClient,
    private val cookies: PersistentCookieJar,
    private val credentials: CredentialStore,
) {
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    private val _username = MutableStateFlow(credentials.load()?.username)
    val username: StateFlow<String?> = _username

    private val loginMutex = Mutex()

    // ---------- HTTP ----------

    /** GET; bij een tijdelijke serverfout (5xx) wordt het na een korte pauze nog één keer geprobeerd. */
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        repeat(2) { attempt ->
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) return@withContext resp.body.string()
                if (resp.code < 500 || attempt == 1) throw IOException("Server gaf ${resp.code}")
            }
            delay(800)
        }
        error("onbereikbaar")
    }

    private suspend fun post(url: String, body: okhttp3.RequestBody): String = postWithUrl(url, body).first

    /** POST; geeft de HTML en de uiteindelijke URL (na doorverwijzingen) terug. */
    private suspend fun postWithUrl(url: String, body: okhttp3.RequestBody): Pair<String, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Referer", url).post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code !in 300..399) throw IOException("Server gaf ${resp.code}")
            resp.body.string() to resp.request.url.toString()
        }
    }

    // ---------- Lezen ----------

    suspend fun recentTopics(page: Int): TopicsPage = topicList(RECENT_URL, page, 40)

    /** Een lijst met onderwerpen: recent, een categorie of "mijn onderwerpen". */
    suspend fun topicList(baseUrl: String, page: Int, perPage: Int, needsLogin: Boolean = false): TopicsPage {
        val url = if (page <= 1) baseUrl else "$baseUrl?start=${(page - 1) * perPage}"
        var html = get(url)
        if (needsLogin && !ForumParser.isLoggedIn(html)) {
            if (!tryAutoLogin()) throw NotLoggedInException()
            html = get(url)
        }
        _loggedIn.value = ForumParser.isLoggedIn(html)
        return withContext(Dispatchers.Default) { ForumParser.parseTopics(html, url) }
    }

    suspend fun forumIndex(): List<Section> {
        val html = get(INDEX_URL)
        _loggedIn.value = ForumParser.isLoggedIn(html)
        return withContext(Dispatchers.Default) { ForumParser.parseIndex(html, INDEX_URL) }
    }

    suspend fun search(query: String, titleOnly: Boolean, user: String, page: Int): SearchPage {
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("titleonly", if (titleOnly) "1" else "0")
            .apply { if (user.isNotBlank()) addQueryParameter("searchuser", user) }
            .addQueryParameter("searchdate", "all")
            .addQueryParameter("sortby", "lastpost")
            .addQueryParameter("order", "dec")
            .addQueryParameter("limit", "25")
            .apply { if (page > 1) addQueryParameter("start", ((page - 1) * 25).toString()) }
            .build().toString()
        val html = get(url)
        return withContext(Dispatchers.Default) { ForumParser.parseSearch(html, url) }
    }

    /**
     * Haalt een pagina van een onderwerp op. Logt eerst automatisch (opnieuw) in
     * als er gegevens zijn opgeslagen maar de sessie verlopen is.
     */
    suspend fun topicPage(url: String): TopicPage {
        var html = get(url)
        var page = withContext(Dispatchers.Default) { ForumParser.parseTopic(html, url) }
        if (!page.loggedIn && credentials.load() != null && tryAutoLogin()) {
            html = get(url)
            page = withContext(Dispatchers.Default) { ForumParser.parseTopic(html, url) }
        }
        _loggedIn.value = page.loggedIn
        return page
    }

    // ---------- Inloggen ----------

    /** Logt in; gooit een [ForumException] met de melding van de site als het mislukt. */
    suspend fun login(username: String, password: String) = loginMutex.withLock {
        // Schone sessie, zodat altijd met het opgegeven account wordt ingelogd.
        cookies.clear()
        val home = get("$BASE/")
        val (action, hidden) = ForumParser.loginForm(home)
            ?: throw ForumException("Inlogformulier niet gevonden op de website")
        val form = FormBody.Builder().apply {
            hidden.forEach { (k, v) -> add(k, v) }
            add("username", username.trim())
            add("passwd", password)
            add("remember", "yes")
            add("Submit", "")
        }.build()
        val result = post(action, form)
        val ok = ForumParser.isLoggedIn(result) || ForumParser.isLoggedIn(get("$BASE/"))
        if (!ok) {
            _loggedIn.value = false
            throw ForumException(ForumParser.loginError(result) ?: "Inloggen mislukt")
        }
        credentials.save(CredentialStore.Credentials(username.trim(), password))
        _username.value = username.trim()
        _loggedIn.value = true
    }

    private suspend fun tryAutoLogin(): Boolean {
        val c = credentials.load() ?: return false
        return runCatching { login(c.username, c.password) }.isSuccess
    }

    fun logout() {
        cookies.clear()
        credentials.clear()
        _username.value = null
        _loggedIn.value = false
    }

    fun hasCredentials() = credentials.load() != null

    // ---------- Reageren ----------

    class Image(val fileName: String, val jpeg: ByteArray)

    suspend fun replyForm(replyUrl: String, topicTitle: String): ReplyForm {
        var html = get(replyUrl)
        if (ForumParser.loginForm(html) != null && tryAutoLogin()) html = get(replyUrl)
        return withContext(Dispatchers.Default) { ForumParser.replyForm(html, replyUrl, topicTitle) }
    }

    /**
     * Plaatst een reactie. Foto's gaan mee als bijlagen kattachment1, kattachment2, …
     * en worden in de tekst opgenomen met [attachment:n]naam[/attachment],
     * net als in de oorspronkelijke app.
     */
    suspend fun postReply(form: ReplyForm, message: String, images: List<Image>, signature: String?) {
        submit(form, form.subject, message, images, signature)
    }

    /** Formulier voor een nieuw onderwerp, via de link "Nieuw onderwerp" van een categorie. */
    suspend fun newTopicForm(createUrl: String): PostForm {
        var html = get(createUrl)
        if (ForumParser.loginForm(html) != null && !ForumParser.isLoggedIn(html)) {
            if (!tryAutoLogin()) throw NotLoggedInException()
            html = get(createUrl)
        }
        return withContext(Dispatchers.Default) { ForumParser.postForm(html, createUrl, "") }
    }

    /** Start een nieuw onderwerp; geeft de URL van het nieuwe onderwerp terug (als bekend). */
    suspend fun createTopic(form: PostForm, subject: String, message: String, images: List<Image>, signature: String?): String? {
        val finalUrl = submit(form, subject.trim(), message, images, signature)
        return finalUrl.takeIf { Regex("/\\d+-[^/]+").containsMatchIn(it) && !it.contains("/create") }
    }

    private suspend fun submit(form: PostForm, subject: String, message: String, images: List<Image>, signature: String?): String {
        val text = buildString {
            append(message.trimEnd())
            images.forEachIndexed { i, img ->
                append("\n\n[attachment:${i + 1}]${img.fileName}[/attachment]")
            }
            if (!signature.isNullOrBlank()) append("\n\n[size=2][i][color=#878787]${signature.trim()}[/color][/i][/size]")
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            form.fields.forEach { (k, v) -> if (k != "subject" && k != "message") addFormDataPart(k, v) }
            addFormDataPart("subject", subject)
            addFormDataPart("message", text)
            images.forEachIndexed { i, img ->
                addFormDataPart("kattachment${i + 1}", img.fileName, img.jpeg.toRequestBody("image/jpeg".toMediaType()))
            }
        }.build()
        val (result, finalUrl) = postWithUrl(form.postUrl, body)
        if (!ForumParser.replySucceeded(result)) {
            throw ForumException(ForumParser.pageError(result) ?: "Bericht niet geplaatst")
        }
        return finalUrl
    }

    companion object {
        const val BASE = ForumParser.BASE
        const val RECENT_URL = "$BASE/index.php/forum-hoofdmenu-37/recente-onderwerpen"
        const val INDEX_URL = "$BASE/index.php/forum-125/index"
        const val SEARCH_URL = "$BASE/index.php/forum-hoofdmenu-37/zoeken"
        const val MY_STARTED_URL = "$BASE/index.php/forum-hoofdmenu-37/topics/user/mode-default"
        const val MY_POSTED_URL = "$BASE/index.php/forum-hoofdmenu-37/topics/user/mode-posted"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) ZeilersforumApp/3"
    }
}
