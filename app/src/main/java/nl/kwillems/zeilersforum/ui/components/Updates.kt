package nl.kwillems.zeilersforum.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.kwillems.zeilersforum.ZfApp
import nl.kwillems.zeilersforum.data.AppRelease
import nl.kwillems.zeilersforum.data.Settings
import nl.kwillems.zeilersforum.data.UpdateState
import java.text.DateFormat
import java.util.Date

/** De sectie "Updates" in de instellingen. */
@Composable
fun UpdateSection(app: ZfApp) {
    val state by app.updates.state.collectAsStateWithLifecycle()
    val s by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // Na terugkomen uit de Android-instellingen opnieuw kijken of installeren mag.
    var canInstall by remember { mutableStateOf(app.updates.canInstall()) }
    LifecycleResumeEffect(Unit) {
        canInstall = app.updates.canInstall()
        onPauseOrDispose { }
    }

    ListItem(
        headlineContent = { Text("Huidige versie ${app.updates.currentVersion}") },
        supportingContent = {
            Text(
                when (val st = state) {
                    UpdateState.Checking -> "Bezig met controleren…"
                    is UpdateState.UpToDate -> "Je hebt de nieuwste versie" + lastCheckText(s.lastUpdateCheck)
                    is UpdateState.Available, is UpdateState.Downloading, is UpdateState.ReadyToInstall -> "Er is een nieuwe versie"
                    is UpdateState.Failed -> st.message
                    UpdateState.Idle -> "Tik om te controleren op een nieuwe versie" + lastCheckText(s.lastUpdateCheck)
                },
            )
        },
        leadingContent = {
            when (state) {
                UpdateState.Checking -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                is UpdateState.UpToDate -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                is UpdateState.Failed -> Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                else -> Icon(Icons.Filled.Refresh, null)
            }
        },
        modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = {
            scope.launch { app.updates.check() }
        }),
    )
    ListItem(
        headlineContent = { Text("Automatisch controleren") },
        supportingContent = { Text("Eens per dag bij het openen van de app") },
        trailingContent = { Switch(checked = s.autoCheckUpdates, onCheckedChange = null) },
        modifier = Modifier.selectable(selected = s.autoCheckUpdates, role = Role.Switch, onClick = {
            scope.launch { app.settings.setAutoCheckUpdates(!s.autoCheckUpdates) }
        }),
    )

    val release: AppRelease? = when (val st = state) {
        is UpdateState.Available -> st.release
        is UpdateState.Downloading -> st.release
        is UpdateState.ReadyToInstall -> st.release
        is UpdateState.Failed -> st.release
        else -> null
    }
    if (release != null) ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NewReleases, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Versie ${release.version} is beschikbaar",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (release.notes.isNotBlank()) Text(
                plainNotes(release.notes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, release.pageUrl.toUri())) }) {
                Text("Bekijk op GitHub")
            }
            when (val st = state) {
                is UpdateState.Downloading -> {
                    LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Downloaden… ${(st.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
                is UpdateState.ReadyToInstall -> {
                    Text(
                        "Gedownload en gecontroleerd (zelfde sleutel" + (if (release.sha256 != null) ", controlesom klopt" else "") + ").",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!canInstall) {
                        Text(
                            "Geef de app eerst toestemming om updates te installeren. Kom daarna terug en tik op Installeren.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = { ctx.startActivity(app.updates.installPermissionIntent()) }) {
                            Text("Toestemming geven")
                        }
                    }
                    Button(onClick = { ctx.startActivity(app.updates.installIntent(st.file)) }, enabled = canInstall) {
                        Icon(Icons.Filled.InstallMobile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Installeren")
                    }
                }
                is UpdateState.Failed -> {
                    Text(st.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { scope.launch { app.updates.download(release) } }) { Text("Opnieuw proberen") }
                }
                else -> Button(onClick = { scope.launch { app.updates.download(release) } }) {
                    Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Downloaden (" + String.format(java.util.Locale("nl"), "%.1f", release.apkSize / 1_048_576.0) + " MB)")
                }
            }
        }
    }
}

/** Melding bovenaan het startscherm als er een nieuwe versie is. */
@Composable
fun UpdateBanner(app: ZfApp, onOpen: () -> Unit) {
    val state by app.updates.state.collectAsStateWithLifecycle()
    val version = when (val st = state) {
        is UpdateState.Available -> st.release.version
        is UpdateState.Downloading -> st.release.version
        is UpdateState.ReadyToInstall -> st.release.version
        else -> null
    } ?: return
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NewReleases, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Nieuwe versie $version", style = MaterialTheme.typography.titleSmall)
                Text("Tik om te bekijken en te installeren", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun lastCheckText(t: Long): String =
    if (t <= 0) "" else " · laatst gecontroleerd " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(t))

/** Releasetekst (Markdown) eenvoudig leesbaar maken: ** en ` weg, installatie-instructie overslaan. */
private fun plainNotes(md: String): String =
    md.lines()
        .filterNot { it.contains("**Installeren:**") }
        .joinToString("\n") { it.replace("**", "").replace("`", "").replace(Regex("^- "), "• ") }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
