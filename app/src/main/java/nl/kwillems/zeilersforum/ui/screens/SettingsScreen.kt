package nl.kwillems.zeilersforum.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.kwillems.zeilersforum.BuildConfig
import nl.kwillems.zeilersforum.ZfApp
import nl.kwillems.zeilersforum.data.PostOrder
import nl.kwillems.zeilersforum.data.RecentPeriod
import nl.kwillems.zeilersforum.data.Settings
import nl.kwillems.zeilersforum.data.ThemeMode
import nl.kwillems.zeilersforum.ui.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: ZfApp, onBack: () -> Unit, onLogin: () -> Unit) {
    val s by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val user by app.forum.username.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editSignature by remember { mutableStateOf(false) }
    var debugResult by remember { mutableStateOf<String?>(null) }
    var choosePeriod by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var appIcon by remember { mutableStateOf(AppIcon.current(ctx)) }

    if (choosePeriod) AlertDialog(
        onDismissRequest = { choosePeriod = false },
        title = { Text("Recente discussies van") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RecentPeriod.entries.forEach { p ->
                    ListItem(
                        headlineContent = { Text(p.label) },
                        leadingContent = { RadioButton(selected = s.recentPeriod == p, onClick = null) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.selectable(selected = s.recentPeriod == p, role = Role.RadioButton, onClick = {
                            scope.launch { app.settings.setRecentPeriod(p) }
                            choosePeriod = false
                        }),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosePeriod = false }) { Text("Sluiten") } },
    )

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
            Section("Recente discussies")
            ListItem(
                headlineContent = { Text("Periode") },
                supportingContent = {
                    Text(
                        when (s.recentPeriod) {
                            RecentPeriod.SINCE_LAST_VISIT -> "Sinds je laatste bezoek (standaard op de site)"
                            RecentPeriod.ALL -> "Alle discussies, ook heel oude"
                            else -> "Discussies van de afgelopen ${s.recentPeriod.label.lowercase()}"
                        },
                    )
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = { choosePeriod = true }),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

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

            Section("App-icoon")
            Text(
                "Tik op een icoon om het te kiezen. Je startscherm toont het na een paar seconden; " +
                    "verdwijnt het daar, zet de app dan opnieuw op je startscherm vanuit de app-lijst.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                AppIcon.entries.forEach { icon ->
                    IconChoice(icon, selected = icon == appIcon) {
                        if (icon != appIcon) {
                            AppIcon.set(ctx, icon)
                            appIcon = icon
                        }
                    }
                }
            }
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
            Section("Updates")
            nl.kwillems.zeilersforum.ui.components.UpdateSection(app)
            Spacer(Modifier.height(24.dp))
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

/** Het app-icoon zoals de launcher het tekent (met de vorm van dit toestel). */
@Composable
private fun AppIconImage(icon: AppIcon, size: androidx.compose.ui.unit.Dp) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(icon, px) {
        androidx.core.content.ContextCompat.getDrawable(ctx, icon.icon)!!.toBitmap(px, px).asImageBitmap()
    }
    androidx.compose.foundation.Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
}

@Composable
private fun IconChoice(icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        Box {
            AppIconImage(icon, 52.dp)
            if (selected) Icon(
                Icons.Filled.CheckCircle, "Gekozen",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).size(20.dp)
                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape),
            )
        }
        Text(
            icon.label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
