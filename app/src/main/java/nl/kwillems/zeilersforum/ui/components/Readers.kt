package nl.kwillems.zeilersforum.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import nl.kwillems.zeilersforum.data.MemberRole
import nl.kwillems.zeilersforum.data.OnlineMember

/** Kleur per rol, zoals de legenda op de site. */
@Composable
private fun roleColor(role: MemberRole): Color = when (role) {
    MemberRole.ADMIN -> Color(0xFFD32F2F)
    MemberRole.GLOBAL_MODERATOR -> Color(0xFF7B1FA2)
    MemberRole.MODERATOR -> Color(0xFF2E7D32)
    MemberRole.BANNED -> MaterialTheme.colorScheme.outline
    MemberRole.USER -> MaterialTheme.colorScheme.primary
}

/**
 * "Wie leest er mee?" als compacte regel: oogje + namen als links (tik = profiel).
 * Lange lijsten tonen eerst twee regels; tik op de regel om alles te zien.
 */
@Composable
fun ReadersCard(readers: List<OnlineMember>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflowing by rememberSaveable { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val colors = readers.map { roleColor(it.role) }
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Medium)) { append("Leest mee: ") }
        readers.forEachIndexed { i, m ->
            if (i > 0) withStyle(SpanStyle(color = muted)) { append(", ") }
            withLink(LinkAnnotation.Url(m.profileUrl, TextLinkStyles(SpanStyle(color = colors[i])))) { append(m.name) }
        }
    }
    Row(
        modifier
            .animateContentSize()
            .clickable(enabled = overflowing || expanded) { expanded = !expanded }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.Visibility, contentDescription = "Wie leest er mee?",
            tint = muted, modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { r: TextLayoutResult -> if (!expanded) overflowing = r.hasVisualOverflow },
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
