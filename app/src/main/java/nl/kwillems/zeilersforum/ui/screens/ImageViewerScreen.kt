package nl.kwillems.zeilersforum.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(images: List<String>, startIndex: Int, onBack: () -> Unit) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))) { images.size }
    var chrome by remember { mutableStateOf(true) }
    val ctx = LocalContext.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(images[page], onTap = { chrome = !chrome })
        }
        if (chrome) TopAppBar(
            title = { if (images.size > 1) Text("${pager.currentPage + 1} / ${images.size}", color = Color.White) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug", tint = Color.White) } },
            actions = {
                IconButton(onClick = {
                    images.getOrNull(pager.currentPage)?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) }
                }) { Icon(Icons.Filled.OpenInBrowser, "Openen in browser", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun ZoomableImage(url: String, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale > 1f) offset + pan * scale else Offset.Zero
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    },
                )
            }
            // Alleen pannen/zoomen afvangen als er ingezoomd is, anders mag de pager vegen.
            .transformable(transform, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Foto",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
        )
    }
}
