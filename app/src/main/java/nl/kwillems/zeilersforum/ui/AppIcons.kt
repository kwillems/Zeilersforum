package nl.kwillems.zeilersforum.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import nl.kwillems.zeilersforum.R

/**
 * Instelbaar app-icoon. Elke variant is een activity-alias in AndroidManifest.xml;
 * precies één staat aan. De icoontjes worden gemaakt met art/iconen/maak_iconen.py.
 */
enum class AppIcon(val alias: String, val label: String, val icon: Int) {
    E("IconE", "Ballon", R.mipmap.ic_app_e),
    AB("IconAB", "Ballon licht", R.mipmap.ic_app_ab),
    DB("IconDB", "Ballon zee", R.mipmap.ic_app_db),
    A("IconA", "Klassiek", R.mipmap.ic_app_a),
    B("IconB", "Diepzee", R.mipmap.ic_app_b),
    C("IconC", "Avond", R.mipmap.ic_app_c),
    D("IconD", "Zee", R.mipmap.ic_app_d),
    F("IconF", "Zeegroen", R.mipmap.ic_app_f),
    N("IconN", "Nacht", R.mipmap.ic_app_n),
    R_("IconR", "Rood", R.mipmap.ic_app_r),
    O("IconO", "Oranje", R.mipmap.ic_app_o),
    M("IconM", "Mist", R.mipmap.ic_app_m);

    companion object {
        /** Staat in het manifest aan. */
        val DEFAULT = E

        // De klassenaam volgt de code (namespace), niet de pakketnaam (die heeft .debug in de testversie).
        private fun component(ctx: Context, icon: AppIcon) =
            ComponentName(ctx.packageName, "nl.kwillems.zeilersforum.${icon.alias}")

        fun current(ctx: Context): AppIcon {
            val pm = ctx.packageManager
            return entries.firstOrNull { icon ->
                when (pm.getComponentEnabledSetting(component(ctx, icon))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == DEFAULT
                    else -> false
                }
            } ?: DEFAULT
        }

        /** Zet [icon] aan en de rest uit. Eerst aanzetten, zodat er altijd een ingang in de app-lijst is. */
        fun set(ctx: Context, icon: AppIcon) {
            val pm = ctx.packageManager
            pm.setComponentEnabledSetting(
                component(ctx, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    component(ctx, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
