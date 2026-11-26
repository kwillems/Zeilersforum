package nl.kwillems.zeilersforum.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import nl.kwillems.zeilersforum.data.ImageMode

/** true als de huidige verbinding geen datalimiet heeft (wifi of ethernet). Volgt wijzigingen live. */
@Composable
fun rememberUnmeteredConnection(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var unmetered by remember { mutableStateOf(cm.isCurrentUnmetered()) }
    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
            override fun onLost(network: Network) {
                unmetered = cm.isCurrentUnmetered()
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return unmetered
}

private fun ConnectivityManager.isCurrentUnmetered(): Boolean =
    getNetworkCapabilities(activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true

/** Of foto's nu automatisch geladen moeten worden, gegeven de instelling. */
@Composable
fun autoLoadImages(mode: ImageMode): Boolean {
    val unmetered = rememberUnmeteredConnection()
    return when (mode) {
        ImageMode.ALWAYS -> true
        ImageMode.WIFI_ONLY -> unmetered
        ImageMode.ON_TAP -> false
    }
}
