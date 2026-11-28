package nl.kwillems.zeilersforum

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import nl.kwillems.zeilersforum.data.CredentialStore
import nl.kwillems.zeilersforum.data.ForumRepository
import nl.kwillems.zeilersforum.data.PersistentCookieJar
import nl.kwillems.zeilersforum.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ZfApp : Application(), SingletonImageLoader.Factory {

    lateinit var cookies: PersistentCookieJar
        private set
    lateinit var client: OkHttpClient
        private set
    lateinit var forum: ForumRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var updates: nl.kwillems.zeilersforum.data.UpdateManager
        private set
    lateinit var readingPositions: nl.kwillems.zeilersforum.data.ReadingPositions
        private set

    override fun onCreate() {
        super.onCreate()
        cookies = PersistentCookieJar(this)
        client = OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
        forum = ForumRepository(
            client, cookies, CredentialStore(this), getSharedPreferences("profile", MODE_PRIVATE),
            nl.kwillems.zeilersforum.data.PageCache(java.io.File(cacheDir, "pages")),
        )
        settings = SettingsRepository(this)
        readingPositions = nl.kwillems.zeilersforum.data.ReadingPositions(getSharedPreferences("reading", MODE_PRIVATE))
        startRecentPrefetch()
        updates = nl.kwillems.zeilersforum.data.UpdateManager(this, client, settings)
        if (BuildConfig.DEBUG) {
            // Alleen de testversie: laatst opgehaalde onderwerppagina bewaren (app-privé map).
            val dir = java.io.File(filesDir, "debug").apply { mkdirs() }
            forum.debugDump = { name, html -> runCatching { java.io.File(dir, "$name.html").writeText(html) } }
        }
    }

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    /**
     * Haalt de Recente discussies al op terwijl het scherm nog wordt opgebouwd; ingelogd kost
     * die pagina het forum zo'n seconde. De lijst pakt het resultaat op (zie data/Prefetched.kt).
     */
    private fun startRecentPrefetch() {
        appScope.launch {
            forum.prefetchTopicList(forum.recentUrl(settings.settings.first().recentPeriod.sel))
        }
    }

    /**
     * Haalt de eerste pagina van een onderwerp alvast op bij een tik, zonder op het resultaat te wachten.
     * Niet al bij aanraken: het forum markeert een opgehaald onderwerp als gelezen.
     */
    fun prefetchTopic(url: String, lastPageUrl: String?) {
        appScope.launch {
            runCatching {
                forum.prefetchTopicPage(nl.kwillems.zeilersforum.ui.screens.TopicViewModel.plannedFirstUrl(this@ZfApp, url, lastPageUrl))
            }
        }
    }

    /** Categorie alvast ophalen bij een tik. */
    fun prefetchCategory(url: String) = forum.prefetchTopicList(url)

    /** Privébericht alvast ophalen bij een tik (openen markeert het toch al als gelezen). */
    fun prefetchPm(id: String, box: nl.kwillems.zeilersforum.data.PmBox) = forum.prefetchPm(id, box)

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .crossfade(true)
            .build()
}
