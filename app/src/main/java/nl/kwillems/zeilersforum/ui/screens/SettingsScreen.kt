package nl.kwillems.zeilersforum.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import nl.kwillems.zeilersforum.data.DEFAULT_SIGNATURE
import nl.kwillems.zeilersforum.data.ImageMode
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.kwillems.zeilersforum.BuildConfig
import nl.kwillems.zeilersforum.ZfApp
import nl.kwillems.zeilersforum.data.PostOrder
import nl.kwillems.zeilersforum.data.Settings
import nl.kwillems.zeilersforum.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: ZfApp, onBack: () -> Unit, onLogin: () -> Unit) {
    val s by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val user by app.forum.username.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editSignature by remember { mutableStateOf(false) }
    var debugResult by remember { mutableStateOf<String?>(null) }

    if (editSignature) {
        var text by remember { mutableStateOf(s.signatureText) }
        AlertDialog(
            onDismissRequest = { editSignature = false },
            title = { Text("Ondertekening") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.replace("\n", " ") },
                        label = { Text("Tekst") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Wordt klein en grijs onder je reactie gezet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { text = DEFAULT_SIGNATURE },
                        enabled = text != DEFAULT_SIGNATURE,
                    ) { Text("Standaardtekst herstellen") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.settings.setSignatureText(text) }
                    editSignature = false
                }) { Text("Opslaan") }
            },
            dismissButton = { TextButton(onClick = { editSignature = false }) { Text("Annuleren") } },
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Instellingen") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Berichten")
            Choice("Oud → nieuw", "Begin bij het eerste bericht van een onderwerp", s.postOrder == PostOrder.OLDEST_FIRST) {
                scope.launch { app.settings.setPostOrder(PostOrder.OLDEST_FIRST) }
            }
            Choice("Nieuw → oud", "Begin bij het nieuwste bericht", s.postOrder == PostOrder.NEWEST_FIRST) {
                scope.launch { app.settings.setPostOrder(PostOrder.NEWEST_FIRST) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("Foto's in berichten")
            Choice("Altijd laden", "Foto's verschijnen meteen in het bericht", s.imageMode == ImageMode.ALWAYS) {
                scope.launch { app.settings.setImageMode(ImageMode.ALWAYS) }
            }
            Choice("Alleen via wifi", "Op mobiele data verschijnt een knop \"Foto bekijken\"", s.imageMode == ImageMode.WIFI_ONLY) {
                scope.launch { app.settings.setImageMode(ImageMode.WIFI_ONLY) }
            }
            Choice("Knop \"Foto bekijken\"", "Foto's pas laden als je erop tikt; bespaart data", s.imageMode == ImageMode.ON_TAP) {
                scope.launch { app.settings.setImageMode(ImageMode.ON_TAP) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("Reageren")
            Toggle("Ondertekening toevoegen", "Een regel onder elke reactie die je met de app plaatst", s.appSignature) {
                scope.launch { app.settings.setAppSignature(it) }
            }
            ListItem(
                headlineContent = { Text("Tekst van de ondertekening") },
                supportingContent = { Text("\"${s.signatureText}\"") },
                leadingContent = { Icon(Icons.Filled.Edit, null) },
                modifier = Modifier
                    .selectable(selected = false, enabled = s.appSignature, onClick = { editSignature = true }, role = Role.Button)
                    .alpha(if (s.appSignature) 1f else 0.5f),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("Weergave")
            Choice("Systeem volgen", null, s.themeMode == ThemeMode.SYSTEM) { scope.launch { app.settings.setThemeMode(ThemeMode.SYSTEM) } }
            Choice("Licht", null, s.themeMode == ThemeMode.LIGHT) { scope.launch { app.settings.setThemeMode(ThemeMode.LIGHT) } }
            Choice("Donker", null, s.themeMode == ThemeMode.DARK) { scope.launch { app.settings.setThemeMode(ThemeMode.DARK) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Toggle("Kleuren van je achtergrond", "Material You", s.dynamicColor) { scope.launch { app.settings.setDynamicColor(it) } }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (BuildConfig.DEBUG) {
                ListItem(
                    headlineContent = { Text("Test: privéberichtpagina's bewaren") },
                    supportingContent = { Text(debugResult ?: "Bewaart de opbouw van postvak in, verzonden en nieuw bericht (lange teksten weggepoetst)") },
                    modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = {
                        scope.launch {
                            val base = "https://zeilersforum.nl/index.php?option=com_uddeim"
                            // Een al gelezen bericht (nonew_im) gebruiken, zodat niets ongemerkt als gelezen wordt gemarkeerd.
                            val readId = app.forum.debugFirstReadMessageId("$base&task=inbox")
                            val pages = mutableMapOf(
                                "pm_inbox" to "$base&task=inbox",
                                "pm_outbox" to "$base&task=outbox",
                                "pm_new" to "$base&task=new",
                            )
                            if (readId != null) {
                                pages["pm_show"] = "$base&task=show&messageid=$readId"
                                pages["pm_reply"] = "$base&task=reply&messageid=$readId"
                            }
                            val n = app.forum.debugSavePages(pages)
                            debugResult = "$n pagina's bewaard"
                        }
                    }),
                )
            }
            Section("Account")
            ListItem(
                headlineContent = { Text(user ?: "Niet ingelogd") },
                supportingContent = { Text(if (user != null) "Tik om uit te loggen of van account te wisselen" else "Tik om in te loggen") },
                leadingContent = { Icon(Icons.Filled.AccountCircle, null) },
                modifier = Modifier.selectable(selected = false, onClick = onLogin, role = Role.Button),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text("Zeilersforum-app") },
                supportingContent = { Text("Versie ${BuildConfig.VERSION_NAME}") },
                leadingContent = { Icon(Icons.Filled.Info, null) },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Choice(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    )
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.selectable(selected = checked, onClick = { onChange(!checked) }, role = Role.Switch),
    )
}
