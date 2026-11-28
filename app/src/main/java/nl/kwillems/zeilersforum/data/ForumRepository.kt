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
    /** Laatst opgehaalde pagina's, om ze meteen te kunnen tonen (zie [PageCache]). */
    private val pageCache: PageCache? = null,
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
        val html = getAsMember(url)
        if (needsLogin && !ForumParser.isLoggedIn(html)) throw NotLoggedInException()
        track(html)
        store(url, html)
        return withContext(Dispatchers.Default) { ForumParser.parseTopics(html, url) }
    }

    // ---------- Bewaarde pagina's (eerst tonen, dan verversen) ----------

    /** Per account apart, zodat je na uitloggen niet ziet wat alleen voor leden is. */
    private fun cacheKey(url: String) = (_username.value ?: "-") + "|" + url.substringBefore('#')

    private suspend fun store(url: String, html: String) {
        val cache = pageCache ?: return
        withContext(Dispatchers.IO) { cache.put(cacheKey(url), html) }
    }

    private suspend fun cached(url: String): String? {
        val cache = pageCache ?: return null
        return withContext(Dispatchers.IO) { cache.get(cacheKey(url)) }
    }

    private fun listUrl(baseUrl: String, page: Int, perPage: Int) =
        if (page <= 1) baseUrl else baseUrl + (if ('?' in baseUrl) "&" else "?") + "start=${(page - 1) * perPage}"

    /** Bewaarde versie van een lijst met onderwerpen, of null. */
    suspend fun cachedTopicList(baseUrl: String, page: Int, perPage: Int): TopicsPage? {
        val url = listUrl(baseUrl, page, perPage)
        val html = cached(url) ?: return null
        return withContext(Dispatchers.Default) { runCatching { ForumParser.parseTopics(html, url) }.getOrNull() }
    }

    suspend fun cachedForumIndex(): List<Section>? {
        val html = cached(INDEX_URL) ?: return null
        return withContext(Dispatchers.Default) { runCatching { ForumParser.parseIndex(html, INDEX_URL) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
    }

    /** Bewaarde pagina van een onderwerp, of null. */
    suspend fun cachedTopicPage(url: String): TopicPage? {
        val html = cached(url) ?: return null
        return withContext(Dispatchers.Default) { runCatching { ForumParser.parseTopic(html, url) }.getOrNull() }
            ?.takeIf { it.posts.isNotEmpty() }
    }

    /** Bewaarde laatste pagina van een onderwerp (voor nieuw → oud), of null. */
    suspend fun cachedLastTopicPage(baseUrl: String): TopicPage? {
        val html = cached(lastPageKey(baseUrl)) ?: return null
        return withContext(Dispatchers.Default) { runCatching { ForumParser.parseTopic(html, baseUrl) }.getOrNull() }
            ?.takeIf { it.posts.isNotEmpty() }
    }

    private fun lastPageKey(baseUrl: String) = ForumParser.baseTopicUrl(baseUrl) + "?laatste"

    suspend fun forumIndex(): List<Section> {
        val html = getAsMember(INDEX_URL)
        track(html)
        store(INDEX_URL, html)
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
        val html = getAsMember(url)
        return withContext(Dispatchers.Default) { ForumParser.parseSearch(html, url) }
    }

    /**
     * Haalt een pagina van een onderwerp op. Logt eerst automatisch (opnieuw) in
     * als er gegevens zijn opgeslagen maar de sessie verlopen is.
     */
    suspend fun topicPage(url: String): TopicPage {
        val html = getAsMember(url)
        val page = withContext(Dispatchers.Default) { ForumParser.parseTopic(html, url) }
        track(html)
        if (page.posts.isNotEmpty()) {
            store(url, html)
            if (page.page >= page.totalPages) store(lastPageKey(url), html)
        }
        debugDump?.invoke(if (page.loggedIn) "topic_ingelogd" else "topic_gast", html)
        return page
    }

    // ---------- Inloggen ----------

    /** Logt in; gooit een [ForumException] met de melding van de site als het mislukt. */
    suspend fun login(username: String, password: String) = loginMutex.withLock {
        // Schone sessie, zodat altijd met het opgegeven account wordt ingelogd.
        cookies.clear()
        if (username.trim() != _username.value) pageCache?.clear()
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

    /**
     * GET als lid: is de sessie verlopen terwijl er inloggegevens zijn opgeslagen, dan eerst
     * opnieuw inloggen. Anders mist de pagina alles wat alleen voor leden is (zoals het Clubhuis).
     */
    private suspend fun getAsMember(url: String): String {
        val html = get(url)
        if (ForumParser.isLoggedIn(html) || credentials.load() == null) return html
        return if (tryAutoLogin()) get(url) else html
    }

    private val autoLoginMutex = Mutex()
    private var lastAutoLogin = 0L
    private var lastAutoLoginFailure = 0L

    /**
     * Opnieuw inloggen; laden er meerdere schermen tegelijk, dan logt er maar één in.
     * Na een mislukte poging (wachtwoord gewijzigd, geen verbinding) wordt het even niet opnieuw geprobeerd.
     */
    private suspend fun tryAutoLogin(): Boolean {
        val c = credentials.load() ?: return false
        val asked = System.currentTimeMillis()
        return autoLoginMutex.withLock {
            if (lastAutoLogin >= asked) return@withLock true
            if (asked - lastAutoLoginFailure < 2 * 60_000) return@withLock false
            runCatching { login(c.username, c.password) }.isSuccess.also {
                if (it) lastAutoLogin = System.currentTimeMillis() else lastAutoLoginFailure = System.currentTimeMillis()
            }
        }
    }

    fun logout() {
        cookies.clear()
        pageCache?.clear()
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

    /**
     * Een bijlage om te versturen. [key] koppelt een foto aan zijn [attachment:key] in de tekst;
     * [inline] = foto (verkleind), anders een bestand.
     */
    class Upload(val fileName: String, val bytes: ByteArray, val mimeType: String, val inline: Boolean, val key: Int? = null)

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
    suspend fun postReply(form: ReplyForm, message: String, images: List<Upload>) {
        submit(form, form.subject, message, images)
    }

    /** Bewerkformulier van een eigen bericht (link "Bewerk"), met de huidige tekst. */
    suspend fun editForm(editUrl: String): PostForm {
        val html = getAsMember(editUrl)
        debugDump?.invoke("bewerken", withContext(Dispatchers.Default) { redact(html, editUrl) })
        return withContext(Dispatchers.Default) { ForumParser.postForm(html, editUrl, "") }
    }

    /**
     * Slaat een bewerkt bericht op. Nieuwe foto's komen erbij; bestaande bijlagen blijven staan,
     * behalve die in [removeAttachments] (hun vakje "behouden" wordt dan niet meegestuurd).
     */
    suspend fun editPost(form: PostForm, subject: String, message: String, images: List<Upload>, removeAttachments: Set<String> = emptySet()) {
        val drop = form.attachments.filter { it.id in removeAttachments }.map { it.keepField }.toSet()
        val kept = form.copy(fields = LinkedHashMap(form.fields.filterKeys { it !in drop }))
        submit(kept, subject.ifBlank { form.subject }, message, images, succeeded = ForumParser::editSucceeded)
    }

    /** Verwijdert een eigen bericht via de link "Verwijder" (bevat het beveiligingstoken van de site). */
    suspend fun deletePost(deleteUrl: String) {
        val html = getAsMember(deleteUrl)
        debugDump?.invoke("verwijderd", withContext(Dispatchers.Default) { redact(html, deleteUrl) })
        if (!ForumParser.isLoggedIn(html)) throw NotLoggedInException()
        ForumParser.deleteError(html)?.let { throw ForumException(it) }
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
    suspend fun createTopic(form: PostForm, subject: String, message: String, images: List<Upload>): String? {
        val finalUrl = submit(form, subject.trim(), message, images)
        return finalUrl.takeIf { Regex("/\\d+-[^/]+").containsMatchIn(it) && !it.contains("/create") }
    }

    private suspend fun submit(
        form: PostForm, subject: String, message: String, images: List<Upload>,
        succeeded: (String) -> Boolean = ForumParser::replySucceeded,
    ): String {
        val text = withAttachmentNumbers(message.trimEnd(), images)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            form.fields.forEach { (k, v) -> if (k != "subject" && k != "message") addFormDataPart(k, v) }
            addFormDataPart("subject", subject)
            addFormDataPart("message", text)
            images.forEachIndexed { i, img ->
                addFormDataPart("kattachment${i + 1}", img.fileName, img.bytes.toRequestBody(img.mimeType.toMediaType()))
            }
        }.build()
        val (result, finalUrl) = postWithUrl(form.postUrl, body)
        debugDump?.invoke("na_versturen", withContext(Dispatchers.Default) { redact(result, finalUrl) })
        if (!succeeded(result)) {
            throw ForumException(ForumParser.pageError(result) ?: "Bericht niet geplaatst")
        }
        return finalUrl
    }

    companion object {
        /**
         * In de tekst staan foto's als [attachment:key]; de site verwacht het nummer van de upload
         * (kattachment1, 2, …). Nummert om. Foto's die niet in de tekst staan, komen alleen als
         * bijlage onder het bericht, net als op de website.
         */
        fun withAttachmentNumbers(message: String, uploads: List<Upload>): String {
            val indexByKey = uploads.withIndex().filter { it.value.key != null }.associate { it.value.key!! to it.index + 1 }
            return Regex("""\[attachment:(\d+)]""").replace(message) { m ->
                indexByKey[m.groupValues[1].toInt()]?.let { "[attachment:$it]" } ?: m.value
            }
        }

        const val BASE = ForumParser.BASE
        const val RECENT_URL = "$BASE/index.php/forum-hoofdmenu-37/recente-onderwerpen"
        const val INDEX_URL = "$BASE/index.php/forum-125/index"
        const val SEARCH_URL = "$BASE/index.php/forum-hoofdmenu-37/zoeken"
        const val MY_STARTED_URL = "$BASE/index.php/forum-hoofdmenu-37/topics/user/mode-started"
        private const val KEY_AVATAR = "avatar"
        const val PM_URL = "$BASE/index.php/component/uddeim/"
        const val MY_POSTED_URL = "$BASE/index.php/forum-hoofdmenu-37/topics/user/mode-posted"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) ZeilersforumApp/3"

        /** Bestandstypen die het forum als bijlage accepteert. */
        val ALLOWED_EXTENSIONS = listOf(
            "jpg", "jpeg", "gif", "png", "eps", "zip", "txt", "doc", "gz", "tgz",
            "docx", "pdf", "xlsx", "odt", "gpx", "ods",
        )
    }
}
