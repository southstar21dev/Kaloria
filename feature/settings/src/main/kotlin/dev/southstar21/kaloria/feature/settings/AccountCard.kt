package dev.southstar21.kaloria.feature.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.sync.AccountState
import java.time.Duration
import java.time.Instant

@Composable
internal fun AccountCard(
    state: AccountState?,
    syncing: Boolean,
    form: SignInForm,
    onEdit: (SignInForm.() -> SignInForm) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val context = LocalContext.current
    // Android 17 blocks connections to private addresses unless the user allows local network access.
    val localNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onSignIn() else onEdit { copy(error = "Allow local network access to reach a server on your network.") }
    }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val canSignIn = !form.busy && form.email.isNotBlank() && form.password.isNotEmpty() && form.serverUrl.isNotBlank()
    val signIn = {
        val needsPermission = isLocalAddress(form.serverUrl) &&
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) localNetwork.launch(LOCAL_NETWORK) else onSignIn()
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sync", style = MaterialTheme.typography.titleSmall)
            val session = state?.session
            if (session == null) {
                Text(
                    "Sign in to your PocketBase server to back up and sync across devices. Everything works offline either way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state?.sync?.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = form.serverUrl,
                    onValueChange = { v -> onEdit { copy(serverUrl = v) } },
                    label = { Text("Server") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.email,
                    onValueChange = { v -> onEdit { copy(email = v) } },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { v -> onEdit { copy(password = v) } },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSignIn) signIn() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                form.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = signIn,
                    enabled = canSignIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (form.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sign in")
                }
                Text(
                    "Accounts are created in the PocketBase admin dashboard; this app can't sign up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.sync.lastError != null) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                        null,
                        tint = if (state.sync.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session.email, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            session.serverUrl.removePrefix("https://").removePrefix("http://"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    when {
                        syncing -> "Syncing…"
                        state.sync.lastError != null -> "Last sync failed: ${state.sync.lastError}"
                        state.sync.lastSyncAt != null -> "Synced ${ago(state.sync.lastSyncAt!!)}"
                        else -> "Not synced yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.sync.lastError != null && !syncing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSyncNow, enabled = !syncing, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Sync, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sync now")
                    }
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.weight(1f)) { Text("Sign out") }
                }
            }
        }
    }
}

private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** localhost, the emulator's host alias, RFC 1918 ranges and mDNS names. */
internal fun isLocalAddress(url: String): Boolean {
    val host = url.trim().substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
    if (host == "localhost" || host.endsWith(".local")) return true
    val octets = host.split('.').map { it.toIntOrNull() ?: return false }
    if (octets.size != 4) return false
    return octets[0] == 10 || octets[0] == 127 ||
        (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 172 && octets[1] in 16..31) ||
        (octets[0] == 169 && octets[1] == 254)
}

private fun ago(millis: Long): String {
    val d = Duration.between(Instant.ofEpochMilli(millis), Instant.now())
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 24 -> "${d.toHours()} h ago"
        else -> "${d.toDays()} days ago"
    }
}
