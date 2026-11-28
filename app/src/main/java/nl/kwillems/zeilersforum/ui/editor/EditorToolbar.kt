package nl.kwillems.zeilersforum.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import nl.kwillems.zeilersforum.data.Smiley

/** Kleuren in de kleurkeuze (uit het palet van de site), met naam voor schermlezers. */
private val COLORS = listOf(
    "#000000" to "Zwart", "#888888" to "Grijs", "#BB0000" to "Donkerrood", "#FF0000" to "Rood",
    "#FF8800" to "Oranje", "#BB8800" to "Okergeel", "#008800" to "Groen", "#004400" to "Donkergroen",
    "#0000FF" to "Blauw", "#000088" to "Donkerblauw", "#8800BB" to "Paars", "#BB0088" to "Roze",
)

/** Lettergroottes zoals op de site ([size=1] … [size=6]). */
private val SIZES = listOf(1 to "Heel klein", 2 to "Klein", 3 to "Normaal", 4 to "Groot", 5 to "Groter", 6 to "Grootst")
private val SIZE_SP = listOf(11, 13, 15, 18, 21, 24)

private enum class UrlKind { LINK, IMAGE, VIDEO }

/**
 * Opmaakbalk (BBCode van het forum) voor het berichtveld: onderaan boven het toetsenbord, of bovenaan.
 * [onFormat] past de tekst aan; [selectedText] geeft de geselecteerde tekst (voor de linktekst).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorToolbar(
    smileys: List<Smiley>,
    selectedText: () -> String,
    onFormat: ((TextFieldValue) -> TextFieldValue) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    /** Bovenaan: scheidingslijn onder de opmaakbalk in plaats van erboven. */
    atTop: Boolean = false,
) {
    var showSmileys by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf<UrlKind?>(null) }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column {
            if (!atTop) HorizontalDivider()
            // De knoppen schuiven opzij als ze niet passen; "Meer" staat vast rechts, zodat hij altijd te vinden is.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(Icons.Filled.FormatBold, "Vet", enabled) { onFormat(BbEditor::bold) }
                ToolButton(Icons.Filled.FormatItalic, "Cursief", enabled) { onFormat(BbEditor::italic) }
                ToolButton(Icons.Filled.FormatUnderlined, "Onderstrepen", enabled) { onFormat(BbEditor::underline) }
                ToolButton(Icons.Filled.FormatStrikethrough, "Doorhalen", enabled) { onFormat(BbEditor::strike) }
                Separator()
                ToolButton(Icons.Filled.FormatQuote, "Citaat", enabled) { onFormat(BbEditor::quote) }
                ToolButton(Icons.Filled.Link, "Link", enabled) { urlDialog = UrlKind.LINK }
                ToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Opsomming", enabled) { onFormat { BbEditor.list(it, ordered = false) } }
                ToolButton(Icons.Filled.FormatListNumbered, "Genummerde lijst", enabled) { onFormat { BbEditor.list(it, ordered = true) } }
                Separator()
                if (smileys.isNotEmpty()) IconToggleButton(checked = showSmileys, onCheckedChange = { showSmileys = it }, enabled = enabled) {
                    Icon(Icons.Filled.EmojiEmotions, "Smileys")
                }
                Box {
                    ToolButton(Icons.Filled.Palette, "Kleur", enabled) { colorMenu = true }
                    DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                        FlowRow(
                            Modifier.widthIn(max = 240.dp).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            COLORS.forEach { (hex, name) ->
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                        .clickable(onClickLabel = name) { colorMenu = false; onFormat { BbEditor.color(it, hex) } },
                                )
                            }
                        }
                    }
                }
                Box {
                    ToolButton(Icons.Filled.FormatSize, "Grootte", enabled) { sizeMenu = true }
                    DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                        SIZES.forEachIndexed { i, (size, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontSize = SIZE_SP[i].sp) },
                                onClick = { sizeMenu = false; onFormat { BbEditor.size(it, size) } },
                            )
                        }
                    }
                }
            }
                Box(Modifier.padding(end = 4.dp)) {
                    ToolButton(Icons.Filled.MoreHoriz, "Meer", enabled) { moreMenu = true }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        MenuRow(Icons.Filled.Image, "Afbeelding via link") { moreMenu = false; urlDialog = UrlKind.IMAGE }
                        MenuRow(Icons.Filled.OndemandVideo, "Video (YouTube-link)") { moreMenu = false; urlDialog = UrlKind.VIDEO }
                        MenuRow(Icons.Filled.Code, "Code") { moreMenu = false; onFormat(BbEditor::code) }
                        MenuRow(Icons.Filled.VisibilityOff, "Spoiler (verborgen tekst)") { moreMenu = false; onFormat(BbEditor::spoiler) }
                        MenuRow(Icons.Filled.FormatAlignCenter, "Centreren") { moreMenu = false; onFormat(BbEditor::center) }
                    }
                }
            }
            if (showSmileys && smileys.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    smileys.forEach { s ->
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .clickable(enabled = enabled, onClickLabel = s.code) { onFormat { BbEditor.smiley(it, s.code) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(model = s.image, contentDescription = s.code, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (atTop) HorizontalDivider()
        }
    }

    urlDialog?.let { kind ->
        UrlDialog(
            kind = kind,
            selected = selectedText(),
            onDismiss = { urlDialog = null },
            onConfirm = { url, label ->
                urlDialog = null
                onFormat {
                    when (kind) {
                        UrlKind.LINK -> BbEditor.link(it, url, label)
                        UrlKind.IMAGE -> BbEditor.image(it, url)
                        UrlKind.VIDEO -> BbEditor.video(it, url)
                    }
                }
            },
        )
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label) }
}

@Composable
private fun Separator() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

/** Vraagt het adres (en bij een link de tekst, tenzij er al tekst is geselecteerd). */
@Composable
private fun UrlDialog(kind: UrlKind, selected: String, onDismiss: () -> Unit, onConfirm: (url: String, label: String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (kind) { UrlKind.LINK -> "Link invoegen"; UrlKind.IMAGE -> "Afbeelding via link"; UrlKind.VIDEO -> "Video invoegen" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = false },
                    label = { Text(if (kind == UrlKind.VIDEO) "Adres van de video" else "Adres") },
                    placeholder = { Text(if (kind == UrlKind.VIDEO) "www.youtube.com/watch?v=…" else "www.voorbeeld.nl") },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) { { Text("Vul een adres in") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == UrlKind.LINK) {
                    if (selected.isNotBlank()) Text("Linktekst: “${selected.take(60)}”", style = MaterialTheme.typography.bodySmall)
                    else OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Tekst (mag leeg)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isBlank()) error = true else onConfirm(url, label) }) { Text("Invoegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
