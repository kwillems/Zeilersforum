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
        forum = ForumRepository(client, cookies, CredentialStore(this), getSharedPreferences("profile", MODE_PRIVATE))
        settings = SettingsRepository(this)
        readingPositions = nl.kwillems.zeilersforum.data.ReadingPositions(getSharedPreferences("reading", MODE_PRIVATE))
        updates = nl.kwillems.zeilersforum.data.UpdateManager(this, client, settings)
        if (BuildConfig.DEBUG) {
            // Alleen de testversie: laatst opgehaalde onderwerppagina bewaren (app-privé map).
            val dir = java.io.File(filesDir, "debug").apply { mkdirs() }
            forum.debugDump = { name, html -> runCatching { java.io.File(dir, "$name.html").writeText(html) } }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .crossfade(true)
            .build()
}
