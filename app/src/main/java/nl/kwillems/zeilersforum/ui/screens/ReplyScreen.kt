package nl.kwillems.zeilersforum.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.kwillems.zeilersforum.ZfApp
import nl.kwillems.zeilersforum.data.ForumRepository
import nl.kwillems.zeilersforum.data.NotLoggedInException
import nl.kwillems.zeilersforum.data.ReplyForm
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class ReplyState(
    val subject: String = "",
    val text: String = "",
    val photos: List<Uri> = emptyList(),
    val form: ReplyForm? = null,
    val loadingForm: Boolean = true,
    val sending: Boolean = false,
    val needsLogin: Boolean = false,
    val error: String? = null,
    val posted: Boolean = false,
    /** Na een nieuw onderwerp: de link ernaartoe. */
    val newTopicUrl: String? = null,
)

class ReplyViewModel(
    private val app: ZfApp,
    private val replyUrl: String,
    private val topicTitle: String,
    quote: String?,
    /** true = nieuw onderwerp starten; [replyUrl] is dan de link "Nieuw onderwerp". */
    val newTopic: Boolean = false,
    /** true = eigen bericht bewerken; [replyUrl] is dan de link "Bewerk". */
    val edit: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(ReplyState(text = quote.orEmpty()))
    val state: StateFlow<ReplyState> = _state
    val title = topicTitle

    init { loadForm() }

    fun loadForm() {
        viewModelScope.launch {
            _state.update { it.copy(loadingForm = true, error = null, needsLogin = false) }
            runCatching {
                when {
                    edit -> app.forum.editForm(replyUrl)
                    newTopic -> app.forum.newTopicForm(replyUrl)
                    else -> app.forum.replyForm(replyUrl, topicTitle)
                }
            }
                .onSuccess { f ->
                    _state.update {
                        // Bij bewerken de huidige tekst en titel invullen (alleen de eerste keer).
                        if (edit && it.form == null) it.copy(form = f, loadingForm = false, text = f.message.trimEnd(), subject = f.subject)
                        else it.copy(form = f, loadingForm = false)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loadingForm = false, needsLogin = e is NotLoggedInException, error = if (e is NotLoggedInException) null else e.friendly())
                    }
                }
        }
    }

    fun setText(t: String) = _state.update { it.copy(text = t) }
    fun setSubject(t: String) = _state.update { it.copy(subject = t) }

    fun canSend(s: ReplyState = _state.value) =
        s.form != null && (s.text.isNotBlank() || s.photos.isNotEmpty()) && (!newTopic || s.subject.isNotBlank())
    fun addPhotos(uris: List<Uri>) = _state.update { it.copy(photos = (it.photos + uris).distinct().take(MAX_PHOTOS)) }
    fun removePhoto(uri: Uri) = _state.update { it.copy(photos = it.photos - uri) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun send(context: Context) {
        val s = _state.value
        val form = s.form ?: return
        if (!canSend(s)) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            runCatching {
                val images = withContext(Dispatchers.Default) {
                    s.photos.mapIndexed { i, uri ->
                        ForumRepository.Image("foto_${System.currentTimeMillis()}_${i + 1}.jpg", compress(context, uri))
                    }
                }
                when {
                    edit -> { app.forum.editPost(form, s.subject, s.text, images); null }
                    newTopic -> app.forum.createTopic(form, s.subject, s.text, images)
                    else -> { app.forum.postReply(form, s.text, images); null }
                }
            }.onSuccess { url ->
                _state.update { it.copy(sending = false, posted = true, newTopicUrl = url) }
            }.onFailure { e ->
                _state.update { it.copy(sending = false, error = e.friendly()) }
            }
        }
    }

    companion object {
        const val MAX_PHOTOS = 6
        private const val MAX_SIDE = 1920

        /** Verkleint de foto tot max. 1920 px, draait hem rechtop en maakt er een JPEG van. */
        fun compress(context: Context, uri: Uri): ByteArray {
            val cr = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            var bmp = cr.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Foto kon niet worden gelezen")
            val rotation = cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val longest = max(bmp.width, bmp.height)
            val factor = if (longest > MAX_SIDE) MAX_SIDE.toFloat() / longest else 1f
            if (rotation != 0f || factor < 1f) {
                val m = Matrix().apply { postScale(factor, factor); postRotate(rotation) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            return ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyScreen(vm: ReplyViewModel, onBack: () -> Unit, onPosted: (newTopicUrl: String?) -> Unit, onLogin: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ReplyViewModel.MAX_PHOTOS),
    ) { uris -> vm.addPhotos(uris) }

    LaunchedEffect(state.posted) { if (state.posted) onPosted(state.newTopicUrl) }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(when { vm.edit -> "Bericht bewerken"; vm.newTopic -> "Nieuw onderwerp"; else -> "Reageren" })
                        Text(vm.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Annuleren") } },
                actions = {
                    if (state.sending) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp))
                    } else {
                        IconButton(
                            onClick = { vm.send(ctx) },
                            enabled = vm.canSend(state),
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Versturen") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            when {
                state.loadingForm -> CircularProgressIndicator(Modifier.padding(32.dp))
                state.needsLogin -> Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(when {
                        vm.edit -> "Je moet ingelogd zijn om een bericht te bewerken."
                        vm.newTopic -> "Je moet ingelogd zijn om een onderwerp te starten."
                        else -> "Je moet ingelogd zijn om te reageren."
                    })
                    Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp)) { Text("Inloggen") }
                }
                state.form == null -> Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "Het formulier kon niet worden geladen.")
                    Button(onClick = vm::loadForm, modifier = Modifier.padding(top = 16.dp)) { Text("Opnieuw") }
                }
                else -> Column(
                    Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (vm.newTopic || (vm.edit && state.form?.subjectEditable == true)) OutlinedTextField(
                        value = state.subject,
                        onValueChange = vm::setSubject,
                        label = { Text("Onderwerp") },
                        singleLine = true,
                        enabled = !state.sending,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = vm::setText,
                        label = { Text(if (vm.newTopic || vm.edit) "Je bericht" else "Je reactie") },
                        minLines = 8,
                        enabled = !state.sending,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !state.sending && state.photos.size < ReplyViewModel.MAX_PHOTOS,
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, null)
                            Text("Foto toevoegen", Modifier.padding(start = 8.dp))
                        }
                        Text(
                            "${state.photos.size}/${ReplyViewModel.MAX_PHOTOS}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    if (state.photos.isNotEmpty()) LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(state.photos) { _, uri ->
                            Box {
                                AsyncImage(
                                    model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)),
                                )
                                IconButton(
                                    onClick = { vm.removePhoto(uri) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)
                                        .clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                ) { Icon(Icons.Filled.Close, "Verwijderen", Modifier.size(16.dp)) }
                            }
                        }
                    }
                    Text(
                        "Tip: opmaak werkt met BBCode, zoals [b]vet[/b] en [i]cursief[/i].",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
