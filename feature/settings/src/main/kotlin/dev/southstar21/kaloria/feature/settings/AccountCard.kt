package dev.southstar21.kaloria.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.sync.AccountState
import dev.southstar21.kaloria.core.ui.LocalClock
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
    val localNetworkDenied = stringResource(R.string.settings_account_local_network_denied)
    val localNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onSignIn() else onEdit { copy(error = localNetworkDenied) }
    }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val canSignIn = !form.busy && form.email.isNotBlank() && form.password.isNotEmpty() && form.serverUrl.isNotBlank()
    val signIn = {
        val needsPermission = isLocalAddress(form.serverUrl) &&
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) localNetwork.launch(LOCAL_NETWORK) else onSignIn()
    }
    ContentCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val session = state?.session
            if (session == null) Text(stringResource(R.string.settings_account_sync_title), style = MaterialTheme.typography.titleSmall)
            if (session == null) {
                Text(
                    stringResource(R.string.settings_account_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state?.sync?.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = form.serverUrl,
                    onValueChange = { v -> onEdit { copy(serverUrl = v) } },
                    label = { Text(stringResource(R.string.settings_account_server)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.email,
                    onValueChange = { v -> onEdit { copy(email = v) } },
                    label = { Text(stringResource(R.string.settings_account_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { v -> onEdit { copy(password = v) } },
                    label = { Text(stringResource(R.string.settings_account_password)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                stringResource(if (showPassword) R.string.settings_account_hide_password else R.string.settings_account_show_password),
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
                    if (form.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.settings_account_sign_in))
                }
                Text(
                    stringResource(R.string.settings_account_no_sign_up),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val failed = state.sync.lastError != null && !syncing
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            session.email.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session.email, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(7.dp).clip(CircleShape)
                                    .background(if (failed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    syncing -> stringResource(R.string.settings_account_syncing)
                                    failed -> stringResource(R.string.settings_account_sync_failed, state.sync.lastError.orEmpty())
                                    state.sync.lastSyncAt != null -> stringResource(R.string.settings_account_synced, ago(context, state.sync.lastSyncAt!!, LocalClock.current.instant()))
                                    else -> stringResource(R.string.settings_account_not_synced)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (failed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = onSyncNow, enabled = !syncing) {
                        if (syncing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.settings_account_sync_now))
                    }
                }
                HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.serverUrl.removePrefix("https://").removePrefix("http://"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSignOut) { Text(stringResource(R.string.settings_account_sign_out)) }
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

private fun ago(context: Context, millis: Long, now: Instant): String {
    val d = Duration.between(Instant.ofEpochMilli(millis), now)
    val res = context.resources
    return when {
        d.toMinutes() < 1 -> res.getString(R.string.settings_account_just_now)
        d.toMinutes() < 60 -> res.getString(R.string.settings_account_minutes_ago, d.toMinutes().toInt())
        d.toHours() < 24 -> res.getString(R.string.settings_account_hours_ago, d.toHours().toInt())
        else -> res.getQuantityString(R.plurals.settings_account_days_ago, d.toDays().toInt(), d.toDays().toInt())
    }
}
