package nl.kwillems.zeilersforum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.kwillems.zeilersforum.data.Settings
import nl.kwillems.zeilersforum.ui.ZfNavHost
import nl.kwillems.zeilersforum.ui.ZfTheme

class MainActivity : ComponentActivity() {

    /** Link naar een onderwerp waarmee de app geopend werd. */
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deepLink.value = intent.topicLink()

        val app = application as ZfApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            ZfTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                ZfNavHost(
                    app = app,
                    deepLink = deepLink.value,
                    onDeepLinkHandled = { deepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.topicLink()?.let { deepLink.value = it }
    }

    private fun Intent.topicLink(): String? =
        if (action == Intent.ACTION_VIEW) dataString?.replace("http://", "https://")?.replace("://www.", "://") else null
}
