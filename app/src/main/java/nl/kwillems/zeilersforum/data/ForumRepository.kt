package nl.kwillems.zeilersforum.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Server gaf ${resp.code}")
            resp.body.string()
        }
    }

    private suspend fun post(url: String, body: okhttp3.RequestBody): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Referer", url).post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code !in 300..399) throw IOException("Server gaf ${resp.code}")
            resp.body.string()
        }
    }

    // ---------- Lezen ----------

    suspend fun recentTopics(page: Int): TopicsPage {
        val url = if (page <= 1) RECENT_URL else "$RECENT_URL?start=${(page - 1) * 40}"
        val html = get(url)
        _loggedIn.value = ForumParser.isLoggedIn(html)
        return withContext(Dispatchers.Default) { ForumParser.parseTopics(html, url) }
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
        val text = buildString {
            append(message.trimEnd())
            images.forEachIndexed { i, img ->
                append("\n\n[attachment:${i + 1}]${img.fileName}[/attachment]")
            }
            if (!signature.isNullOrBlank()) append("\n\n[size=2][i][color=#878787]${signature.trim()}[/color][/i][/size]")
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            form.fields.forEach { (k, v) -> addFormDataPart(k, v) }
            addFormDataPart("subject", form.subject)
            addFormDataPart("message", text)
            images.forEachIndexed { i, img ->
                addFormDataPart("kattachment${i + 1}", img.fileName, img.jpeg.toRequestBody("image/jpeg".toMediaType()))
            }
        }.build()
        val result = post(form.postUrl, body)
        if (!ForumParser.replySucceeded(result)) {
            throw ForumException(ForumParser.pageError(result) ?: "Bericht niet geplaatst")
        }
    }

    companion object {
        const val BASE = ForumParser.BASE
        const val RECENT_URL = "$BASE/index.php/forum-hoofdmenu-37/recente-onderwerpen"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) ZeilersforumApp/3"
    }
}
