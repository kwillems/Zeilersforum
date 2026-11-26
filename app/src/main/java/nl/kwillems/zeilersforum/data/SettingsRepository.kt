package nl.kwillems.zeilersforum.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val postOrder: PostOrder = PostOrder.OLDEST_FIRST,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val appSignature: Boolean = true,
    val signatureText: String = DEFAULT_SIGNATURE,
    val imageMode: ImageMode = ImageMode.ALWAYS,
)

/** Wanneer foto's in berichten automatisch worden geladen. */
enum class ImageMode { ALWAYS, WIFI_ONLY, ON_TAP }

const val DEFAULT_SIGNATURE = "Gepost met de vernieuwde Zeilersforum-app"

class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            postOrder = p[ORDER]?.let { runCatching { PostOrder.valueOf(it) }.getOrNull() } ?: PostOrder.OLDEST_FIRST,
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[DYNAMIC] ?: true,
            appSignature = p[SIGNATURE] ?: true,
            signatureText = p[SIGNATURE_TEXT]?.takeIf { it.isNotBlank() } ?: DEFAULT_SIGNATURE,
            imageMode = p[IMAGE_MODE]?.let { runCatching { ImageMode.valueOf(it) }.getOrNull() }
                // Overgang van de oude aan/uit-schakelaar.
                ?: if (p[IMAGES] == false) ImageMode.ON_TAP else ImageMode.ALWAYS,
        )
    }

    suspend fun setPostOrder(v: PostOrder) = context.dataStore.edit { it[ORDER] = v.name }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[THEME] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[DYNAMIC] = v }
    suspend fun setAppSignature(v: Boolean) = context.dataStore.edit { it[SIGNATURE] = v }
    suspend fun setImageMode(v: ImageMode) = context.dataStore.edit { it[IMAGE_MODE] = v.name; it.remove(IMAGES) }

    /** Lege tekst = terug naar de standaardondertekening. */
    suspend fun setSignatureText(v: String) = context.dataStore.edit {
        if (v.isBlank() || v.trim() == DEFAULT_SIGNATURE) it.remove(SIGNATURE_TEXT) else it[SIGNATURE_TEXT] = v.trim()
    }

    private companion object {
        val ORDER = stringPreferencesKey("post_order")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val SIGNATURE = booleanPreferencesKey("app_signature")
        val IMAGES = booleanPreferencesKey("load_images")
        val IMAGE_MODE = stringPreferencesKey("image_mode")
        val SIGNATURE_TEXT = stringPreferencesKey("signature_text")
    }
}
