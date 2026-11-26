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
    private val profilePrefs: android.content.SharedPreferences,
) {
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    private val _username = MutableStateFlow(credentials.load()?.username)
    val username: StateFlow<String?> = _username

    private val loginMutex = Mutex()

    /** Aantal ongelezen privéberichten (uit de zijmodule op elke pagina), null = onbekend/niet ingelogd. */
    private val _pmUnread = MutableStateFlow<Int?>(null)
    val pmUnread: StateFlow<Int?> = _pmUnread

    /** Eigen avatar (URL), bewaard zodat hij meteen zichtbaar is bij het openen van de app. */
    private val _avatar = MutableStateFlow(profilePrefs.getString(KEY_AVATAR, null))
    val avatar: StateFlow<String?> = _avatar

    private fun setAvatar(url: String?) {
        if (url == _avatar.value) return
        _avatar.value = url
        profilePrefs.edit().apply { if (url == null) remove(KEY_AVATAR) else putString(KEY_AVATAR, url) }.apply()
    }

    private fun track(html: String) {
        val loggedIn = ForumParser.isLoggedIn(html)
        _loggedIn.value = loggedIn
        _pmUnread.value = if (loggedIn) ForumParser.pmUnread(html) ?: _pmUnread.value else null
        if (loggedIn) ForumParser.myAvatar(org.jsoup.Jsoup.parse(html, BASE))?.let(::setAvatar)
    }

    /** Zorgt dat de avatar bekend is (bijvoorbeeld na een update van de app). */
    suspend fun ensureAvatar() {
        val user = _username.value ?: return
        if (_avatar.value == null && _loggedIn.value) lookupAvatar(user)
    }

    /** Reserve: avatar opzoeken via een bericht van jezelf in de zoekfunctie. */
    private suspend fun lookupAvatar(username: String) {
        runCatching {
            val url = SEARCH_URL.toHttpUrl().newBuilder()
                .addQueryParameter("query", "")
                .addQueryParameter("searchuser", username)
                .addQueryParameter("exactname", "1")
                .addQueryParameter("titleonly", "0")
                .addQueryParameter("searchdate", "all")
                .addQueryParameter("sortby", "lastpost")
                .addQueryParameter("order", "dec")
                .addQueryParameter("limit", "5")
                .build().toString()
            ForumParser.avatarOf(get(url), username)
        }.getOrNull()?.let(::setAvatar)
    }

    /** Alleen in de testversie: bewaart opgehaalde HTML lokaal om de opbouw van de site te bekijken. */
    var debugDump: ((name: String, html: String) -> Unit)? = null

    /**
     * Alleen testversie: haalt pagina's op en bewaart ze lokaal met weggepoetste lange teksten,
     * zodat de opbouw te bekijken is zonder de inhoud van berichten.
     */
    suspend fun debugSavePages(pages: Map<String, String>): Int {
        var n = 0
        for ((name, url) in pages) {
            val html = runCatching { get(url) }.getOrNull() ?: continue
            debugDump?.invoke(name, withContext(Dispatchers.Default) { redact(html, url) })
            n++
        }
        return n
    }

    suspend fun debugFirstReadMessageId(inboxUrl: String): String? {
        val doc = org.jsoup.Jsoup.parse(get(inboxUrl), inboxUrl)
        return doc.select("#uddeim-overview tr").firstOrNull { it.selectFirst("img[src*=nonew_im]") != null }
            ?.selectFirst("input[name=arcmes[]]")?.attr("value")?.takeIf { it.isNotBlank() }
    }

    private fun redact(html: String, url: String): String {
        val doc = org.jsoup.Jsoup.parse(html, url)
        doc.select("script, style").remove()
        doc.traverse { node, _ ->
            if (node is org.jsoup.nodes.TextNode && node.text().trim().length > 30) node.text("[tekst]")
            if (node is org.jsoup.nodes.Element) {
                for (attr in listOf("title", "alt", "value", "content")) {
                    val v = node.attr(attr)
                    if (v.length > 30 && !(node.tagName() == "input" && node.attr("type") == "hidden")) node.attr(attr, "[tekst]")
                }
            }
        }
        doc.select("textarea").forEach { it.text("") }
        return doc.outerHtml()
    }

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

    /** Adres van "Recente discussies" voor een periode (sel = waarde uit de keuzelijst van de site). */
    fun recentUrl(sel: Int): String = if (sel == 0) RECENT_URL else "$RECENT_URL?sel=$sel"

    /** Een lijst met onderwerpen: recent, een categorie of "mijn onderwerpen". */
    suspend fun topicList(baseUrl: String, page: Int, perPage: Int, needsLogin: Boolean = false): TopicsPage {
        val url = if (page <= 1) baseUrl else baseUrl + (if ('?' in baseUrl) "&" else "?") + "start=${(page - 1) * perPage}"
        var html = get(url)
        if (needsLogin && !ForumParser.isLoggedIn(html)) {
            if (!tryAutoLogin()) throw NotLoggedInException()
            html = get(url)
        }
        track(html)
        return withContext(Dispatchers.Default) { ForumParser.parseTopics(html, url) }
    }

    suspend fun forumIndex(): List<Section> {
        val html = get(INDEX_URL)
        track(html)
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
        track(html)
        debugDump?.invoke(if (page.loggedIn) "topic_ingelogd" else "topic_gast", html)
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
        if (_username.value != username.trim()) setAvatar(null)
        _username.value = username.trim()
        _loggedIn.value = true
        ForumParser.myAvatar(org.jsoup.Jsoup.parse(result, BASE))?.let(::setAvatar)
        if (_avatar.value == null) lookupAvatar(username.trim())
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
        _pmUnread.value = null
        setAvatar(null)
    }

    // ---------- Privéberichten (uddeIM) ----------

    /** Haalt een pagina op waarvoor je ingelogd moet zijn; logt zo nodig eerst automatisch in. */
    private suspend fun getLoggedIn(url: String): String {
        var html = get(url)
        if (!ForumParser.isLoggedIn(html)) {
            if (!tryAutoLogin()) throw NotLoggedInException()
            html = get(url)
        }
        track(html)
        return html
    }

    suspend fun pmList(box: PmBox, page: Int): PmPage {
        val task = if (box == PmBox.INBOX) "inbox" else "outbox"
        val url = "$PM_URL?task=$task" + if (page > 1) "&limit=20&start=${(page - 1) * 20}" else ""
        val html = getLoggedIn(url)
        return withContext(Dispatchers.Default) { ForumParser.parsePmList(html, url) }
    }

    suspend fun pmMessage(id: String, box: PmBox): PmMessage {
        val url = "$PM_URL?task=${if (box == PmBox.INBOX) "show" else "showout"}&messageid=$id"
        val html = getLoggedIn(url)
        return withContext(Dispatchers.Default) { ForumParser.parsePm(html, url) }
    }

    suspend fun pmNewForm(): PostForm {
        val url = "$PM_URL?task=new"
        val html = getLoggedIn(url)
        return withContext(Dispatchers.Default) { ForumParser.pmNewForm(html, url) }
    }

    /** Verstuurt een privébericht. [toName] alleen bij een nieuw bericht (bij beantwoorden staat de ontvanger in het formulier). */
    suspend fun pmSend(form: PostForm, text: String, toName: String?, copyToMe: Boolean) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            form.fields.forEach { (k, v) -> if (k != "to_name" || toName == null) addFormDataPart(k, v) }
            if (toName != null) addFormDataPart("to_name", toName.trim())
            addFormDataPart("pmessage", text)
            if (copyToMe) addFormDataPart("copytome", "1")
            addFormDataPart("addccinfo", "1")
            addFormDataPart("reply", "Verstuur")
        }.build()
        val (html, _) = postWithUrl(form.postUrl, body)
        ForumParser.pmSendError(html, text)?.let { throw ForumException(it) }
        track(html)
    }

    suspend fun pmDelete(id: String, box: PmBox) {
        val url = if (box == PmBox.INBOX) "$PM_URL?task=delete&messageid=$id&ret=top"
        else "$PM_URL?task=deletefromoutbox&messageid=$id&limit=20&start=0"
        getLoggedIn(url)
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
        const val MY_STARTED_URL = "$BASE/index.php/forum-hoofdmenu-37/topics/user/mode-started"
        private const val KEY_AVATAR = "avatar"
        const val PM_URL = "$BASE/index.php/component/uddeim/"
        const val MY_POSTED_URL = "$BASE/index.php/forum-hoofdmenu-37/topics/user/mode-posted"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) ZeilersforumApp/3"
    }
}
