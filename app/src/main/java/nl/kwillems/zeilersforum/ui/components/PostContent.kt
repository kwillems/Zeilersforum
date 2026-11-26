package nl.kwillems.zeilersforum.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.SubcomposeAsyncImage
import nl.kwillems.zeilersforum.data.Block

/** Toont de inhoud van een bericht: tekst, foto's, citaten en video's. */
@Composable
fun PostContent(
    blocks: List<Block>,
    loadImages: Boolean,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Text -> HtmlText(block.html)
                is Block.Image -> PostImage(block.src, loadImages, onClick = { onImageClick(block.src) })
                is Block.Quote -> QuoteBox(block.blocks, loadImages, onImageClick)
                is Block.Video -> VideoCard(block.url)
            }
        }
    }
}

@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(html, linkColor) {
        AnnotatedString.fromHtml(
            html,
            linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
        )
    }
    Text(
        text, style = MaterialTheme.typography.bodyLarge, modifier = modifier,
        maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

@Composable
private fun QuoteBox(blocks: List<Block>, loadImages: Boolean, onImageClick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        PostContent(blocks, loadImages, onImageClick, Modifier.padding(12.dp))
    }
}

@Composable
fun PostImage(src: String, loadImages: Boolean, onClick: () -> Unit) {
    if (!loadImages) {
        OutlinedCard(onClick = onClick) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Image, null)
                Text("Foto bekijken", Modifier.padding(start = 8.dp))
            }
        }
        return
    }
    SubcomposeAsyncImage(
        model = src,
        contentDescription = "Foto",
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        loading = {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 160.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.outline) }
        },
        error = {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BrokenImage, null, tint = MaterialTheme.colorScheme.outline)
                    Text(
                        "Foto niet beschikbaar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun VideoCard(url: String) {
    val ctx = LocalContext.current
    OutlinedCard(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayCircle, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text("Video bekijken", style = MaterialTheme.typography.titleSmall)
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun ErrorPane(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Filled.Refresh, null)
                    Text("Opnieuw", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
