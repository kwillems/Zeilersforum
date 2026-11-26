package nl.kwillems.zeilersforum.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.kwillems.zeilersforum.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Een release op GitHub met een APK. */
data class AppRelease(
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    /** SHA-256 van de APK volgens GitHub, of null als GitHub die niet geeft. */
    val sha256: String?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: String) : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class ReadyToInstall(val release: AppRelease, val file: File) : UpdateState
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateState
}

/**
 * Zoekt nieuwe versies in de releases van github.com/kwillems/Zeilersforum,
 * downloadt de APK, controleert die en start de installatie.
 */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class GhAsset(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val url: String,
        val digest: String? = null,
    )

    @Serializable private data class GhRelease(
        @SerialName("tag_name") val tag: String,
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GhAsset> = emptyList(),
    )

    val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Controleert op een nieuwe versie. [quiet] = automatische controle: geen foutmelding tonen. */
    suspend fun check(quiet: Boolean = false) {
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        val result = runCatching { latestRelease() }
        settings.setLastUpdateCheck(System.currentTimeMillis())
        _state.value = result.fold(
            onSuccess = { rel ->
                when {
                    rel == null -> UpdateState.UpToDate(currentVersion)
                    isNewer(rel.version, currentVersion) -> UpdateState.Available(rel)
                    else -> UpdateState.UpToDate(rel.version)
                }
            },
            onFailure = { e -> if (quiet) UpdateState.Idle else UpdateState.Failed(e.message ?: "Controleren mislukt") },
        )
    }

    private suspend fun latestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ZeilersforumApp/${BuildConfig.VERSION_NAME}")
            .build()
        val body = client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) throw IOException("GitHub gaf ${resp.code}")
            resp.body.string()
        }
        val r = json.decodeFromString<GhRelease>(body)
        if (r.draft || r.prerelease) return@withContext null
        val apk = r.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
        AppRelease(
            version = r.tag.removePrefix("v"),
            title = r.name ?: r.tag,
            notes = r.body.orEmpty(),
            pageUrl = r.htmlUrl,
            apkUrl = apk.url,
            apkName = apk.name,
            apkSize = apk.size,
            sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
        )
    }

    /** Downloadt de APK met voortgang en controleert controlesom, pakketnaam, versie en sleutel. */
    suspend fun download(release: AppRelease) {
        _state.value = UpdateState.Downloading(release, 0f)
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, release.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                val digest = MessageDigest.getInstance("SHA-256")
                val req = Request.Builder().url(release.apkUrl).header("User-Agent", "ZeilersforumApp/${BuildConfig.VERSION_NAME}").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Download mislukt (${resp.code})")
                    val total = resp.body.contentLength().takeIf { it > 0 } ?: release.apkSize
                    resp.body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastEmit = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                digest.update(buf, 0, n)
                                done += n
                                if (done - lastEmit > 64 * 1024 && total > 0) {
                                    lastEmit = done
                                    _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
                release.sha256?.let { expected ->
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (actual != expected) { file.delete(); throw IOException("Controlesom klopt niet; download verwijderd") }
                }
                verifyApk(file)
                file
            }
        }
        _state.value = result.fold(
            onSuccess = { UpdateState.ReadyToInstall(release, it) },
            onFailure = { UpdateState.Failed(it.message ?: "Download mislukt", release) },
        )
    }

    /** Zelfde pakketnaam en zelfde ondertekensleutel als de geïnstalleerde app, anders weigeren. */
    @Suppress("DEPRECATION")
    private fun verifyApk(file: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk = pm.getPackageArchiveInfo(file.path, flags) ?: throw IOException("Gedownload bestand is geen geldige app")
        val installedPkg = context.packageName
        // De testversie heeft een eigen naam (.debug); die vergelijkt met de gewone pakketnaam.
        if (apk.packageName != installedPkg.removeSuffix(".debug")) throw IOException("Gedownloade app heeft een andere pakketnaam")
        val installed = pm.getPackageInfo(installedPkg, flags)
        fun sigs(info: android.content.pm.PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.toList() else info.signatures?.toList())
                .orEmpty().map { it.toCharsString() }.toSet()
        if (sigs(apk).isEmpty() || sigs(apk) != sigs(installed)) {
            file.delete()
            throw IOException("Gedownloade app is niet met dezelfde sleutel ondertekend; niet geïnstalleerd")
        }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opent de Android-instelling "Apps van deze bron toestaan" voor deze app. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Start de installatie van Android voor het gedownloade bestand. */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun reset() = _state.update { s -> if (s is UpdateState.Failed && s.release != null) UpdateState.Available(s.release) else UpdateState.Idle }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/kwillems/Zeilersforum/releases/latest"
        const val AUTO_CHECK_INTERVAL = 24L * 60 * 60 * 1000

        /** Vergelijkt versies als 3.0.10 > 3.0.9. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate); val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
