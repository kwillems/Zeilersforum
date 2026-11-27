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
    val imageMode: ImageMode = ImageMode.ALWAYS,
    /** Eens per dag bij het openen kijken of er een nieuwe versie op GitHub staat. */
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0,
    /** Hoe ver "Recente discussies" teruggaat (keuzelijst van de site). */
    val recentPeriod: RecentPeriod = RecentPeriod.SINCE_LAST_VISIT,
)

/** De periodes uit de keuzelijst op de site; [sel] is de waarde die de site verwacht. */
enum class RecentPeriod(val sel: Int, val label: String) {
    SINCE_LAST_VISIT(0, "Sinds laatste bezoek"),
    HOURS_4(4, "4 uur"),
    HOURS_8(8, "8 uur"),
    HOURS_12(12, "12 uur"),
    HOURS_24(24, "24 uur"),
    HOURS_48(48, "48 uur"),
    WEEK(168, "Week"),
    MONTH(720, "Maand"),
    YEAR(8760, "Jaar"),
    ALL(-1, "Alle"),
}

/** Wanneer foto's in berichten automatisch worden geladen. */
enum class ImageMode { ALWAYS, WIFI_ONLY, ON_TAP }


class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            postOrder = p[ORDER]?.let { runCatching { PostOrder.valueOf(it) }.getOrNull() } ?: PostOrder.OLDEST_FIRST,
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[DYNAMIC] ?: true,
            imageMode = p[IMAGE_MODE]?.let { runCatching { ImageMode.valueOf(it) }.getOrNull() }
                // Overgang van de oude aan/uit-schakelaar.
                ?: if (p[IMAGES] == false) ImageMode.ON_TAP else ImageMode.ALWAYS,
            autoCheckUpdates = p[AUTO_UPDATE] ?: true,
            lastUpdateCheck = p[LAST_UPDATE_CHECK] ?: 0,
            recentPeriod = p[RECENT_PERIOD]?.let { runCatching { RecentPeriod.valueOf(it) }.getOrNull() } ?: RecentPeriod.SINCE_LAST_VISIT,
        )
    }

    suspend fun setPostOrder(v: PostOrder) = context.dataStore.edit { it[ORDER] = v.name }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[THEME] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[DYNAMIC] = v }
    suspend fun setAutoCheckUpdates(v: Boolean) = context.dataStore.edit { it[AUTO_UPDATE] = v }
    suspend fun setLastUpdateCheck(v: Long) = context.dataStore.edit { it[LAST_UPDATE_CHECK] = v }
    suspend fun setRecentPeriod(v: RecentPeriod) = context.dataStore.edit { it[RECENT_PERIOD] = v.name }
    suspend fun setImageMode(v: ImageMode) = context.dataStore.edit { it[IMAGE_MODE] = v.name; it.remove(IMAGES) }


    private companion object {
        val ORDER = stringPreferencesKey("post_order")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val IMAGES = booleanPreferencesKey("load_images")
        val IMAGE_MODE = stringPreferencesKey("image_mode")
        val AUTO_UPDATE = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = androidx.datastore.preferences.core.longPreferencesKey("last_update_check")
        val RECENT_PERIOD = stringPreferencesKey("recent_period")
    }
}
