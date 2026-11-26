package nl.kwillems.zeilersforum.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Bewaart cookies (sessie en "onthoud mij") zodat je ingelogd blijft
 * nadat de app is afgesloten.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    @Serializable
    private data class Stored(
        val name: String, val value: String, val expiresAt: Long, val domain: String,
        val path: String, val secure: Boolean, val httpOnly: Boolean, val hostOnly: Boolean,
        val persistent: Boolean,
    )

    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val cookies = mutableListOf<Cookie>()

    init {
        prefs.getString(KEY, null)?.let { raw ->
            runCatching { json.decodeFromString<List<Stored>>(raw) }.getOrNull()?.forEach { s ->
                val b = Cookie.Builder().name(s.name).value(s.value).path(s.path)
                if (s.hostOnly) b.hostOnlyDomain(s.domain) else b.domain(s.domain)
                if (s.persistent) b.expiresAt(s.expiresAt)
                if (s.secure) b.secure()
                if (s.httpOnly) b.httpOnly()
                cookies += b.build()
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            if (c.expiresAt > System.currentTimeMillis()) this.cookies += c
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        if (cookies.removeAll { it.expiresAt < now }) persist()
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        persist()
    }

    private fun persist() {
        // Alleen blijvende cookies en sessiecookies; sessiecookies krijgen geen vervaldatum mee.
        val list = cookies.map {
            Stored(it.name, it.value, it.expiresAt, it.domain, it.path, it.secure, it.httpOnly, it.hostOnly, it.persistent)
        }
        prefs.edit().putString(KEY, json.encodeToString(list)).apply()
    }

    private companion object {
        const val KEY = "cookies"
    }
}
