package nl.kwillems.zeilersforum.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.kwillems.zeilersforum.ZfApp

data class LoginState(val busy: Boolean = false, val error: String? = null, val done: Boolean = false)

class LoginViewModel(private val app: ZfApp) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state
    val username = app.forum.username
    val loggedIn = app.forum.loggedIn

    fun login(user: String, pass: String) {
        viewModelScope.launch {
            _state.update { LoginState(busy = true) }
            runCatching { app.forum.login(user, pass) }
                .onSuccess { _state.update { LoginState(done = true) } }
                .onFailure { e -> _state.update { LoginState(error = e.friendly()) } }
        }
    }

    fun logout() {
        app.forum.logout()
        _state.update { LoginState() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: LoginViewModel, onBack: () -> Unit, onLoggedIn: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val savedUser by vm.username.collectAsStateWithLifecycle()
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    var user by rememberSaveable { mutableStateOf(savedUser.orEmpty()) }
    var pass by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }

    if (state.done) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onLoggedIn() }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Account") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug") } },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.AccountCircle, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                if (savedUser != null) {
                    Text(
                        if (loggedIn) "Ingelogd als $savedUser" else "Opgeslagen account: $savedUser",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = { vm.logout(); user = ""; pass = "" }) { Text("Uitloggen en gegevens wissen") }
                    Text("Of log in met een ander account:", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Log in met je account van zeilersforum.nl", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Gebruikersnaam") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Wachtwoord") },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) "Verberg" else "Toon")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (user.isNotBlank() && pass.isNotEmpty()) vm.login(user, pass) }),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { vm.login(user, pass) },
                    enabled = !state.busy && user.isNotBlank() && pass.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Inloggen")
                }
                Text(
                    "Je wachtwoord wordt versleuteld op dit toestel bewaard, zodat de app automatisch opnieuw kan inloggen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
