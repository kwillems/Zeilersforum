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

    override fun onCreate() {
        super.onCreate()
        cookies = PersistentCookieJar(this)
        client = OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
        forum = ForumRepository(client, cookies, CredentialStore(this))
        settings = SettingsRepository(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .crossfade(true)
            .build()
}
