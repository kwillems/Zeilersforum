package nl.kwillems.zeilersforum.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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

/** "Wie leest er mee?": de leden die dit onderwerp nu bekijken. Tik op een naam voor het profiel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadersCard(readers: List<OnlineMember>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    OutlinedCard(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Visibility, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    "Wie leest er mee?",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                Text(
                    readers.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                readers.forEach { m ->
                    val color = roleColor(m.role)
                    AssistChip(
                        onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, m.profileUrl.toUri())) },
                        label = { Text(m.name) },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(color)) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = if (m.role == MemberRole.USER) MaterialTheme.colorScheme.onSurface else color,
                        ),
                    )
                }
            }
        }
    }
}
