package dev.southstar21.kaloria.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.sync.AccountState
import dev.southstar21.kaloria.core.sync.PendingSwitch
import dev.southstar21.kaloria.core.sync.SignInException
import dev.southstar21.kaloria.core.sync.SwitchChoice
import dev.southstar21.kaloria.core.ui.LocalClock
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AccountCard(
    state: AccountState?,
    syncing: Boolean,
    form: SignInForm,
    onEdit: (SignInForm.() -> SignInForm) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onResolveSwitch: (SwitchChoice) -> Unit = {},
    onCancelSwitch: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Android 17 blocks connections to local network addresses unless the user allows local network access.
    val localNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onSignIn() else onEdit { copy(errorRes = R.string.settings_account_local_network_denied) }
    }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var checkingAddress by remember { mutableStateOf(false) }
    val canSignIn = !form.busy && !checkingAddress && form.email.isNotBlank() && form.password.isNotEmpty() && form.serverUrl.isNotBlank()
    val signIn: () -> Unit = {
        val asked = Build.VERSION.SDK_INT < LOCAL_NETWORK_SDK ||
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        if (asked) {
            onSignIn()
        } else {
            val url = form.serverUrl
            scope.launch {
                checkingAddress = true
                val local = try {
                    needsLocalNetwork(url)
                } finally {
                    checkingAddress = false
                }
                if (local) localNetwork.launch(LOCAL_NETWORK) else onSignIn()
            }
        }
    }
    state?.pendingSwitch?.let { SwitchAccountDialog(it, onResolveSwitch, onCancelSwitch) }
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
                    placeholder = { Text(stringResource(R.string.settings_account_server_placeholder)) },
                    // Release builds come with no server filled in: say what goes here.
                    supportingText = if (form.serverUrl.isBlank()) {
                        { Text(stringResource(R.string.settings_account_server_hint)) }
                    } else {
                        null
                    },
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
                form.errorRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = signIn,
                    enabled = canSignIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (form.busy || checkingAddress) {
                        val signingIn = stringResource(R.string.settings_account_signing_in)
                        CircularProgressIndicator(Modifier.size(18.dp).semantics { contentDescription = signingIn }, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.settings_account_sign_in))
                    }
                }
                Text(
                    stringResource(R.string.settings_account_no_sign_up),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val failed = state.sync.lastError != null && !syncing
                // Synced, but some changes didn't make it: not a clean success.
                val incomplete = !failed && !syncing && state.sync.rejected > 0
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
                                    .background(if (failed || incomplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    syncing -> stringResource(R.string.settings_account_syncing)

                                    failed -> stringResource(R.string.settings_account_sync_failed, state.sync.lastError.orEmpty())

                                    incomplete -> pluralStringResource(
                                        R.plurals.settings_account_synced_with_rejected,
                                        state.sync.rejected,
                                        state.sync.lastSyncAt?.let { ago(context, it, LocalClock.current.instant()) }.orEmpty(),
                                        state.sync.rejected,
                                    )

                                    state.sync.lastSyncAt != null -> stringResource(R.string.settings_account_synced, ago(context, state.sync.lastSyncAt!!, LocalClock.current.instant()))

                                    else -> stringResource(R.string.settings_account_not_synced)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (failed || incomplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = onSyncNow, enabled = !syncing) {
                        if (syncing) {
                            val syncingNow = stringResource(R.string.settings_account_syncing)
                            CircularProgressIndicator(Modifier.size(16.dp).semantics { contentDescription = syncingNow }, strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.settings_account_sync_now))
                        }
                    }
                }
                if (state.sync.stale > 0 && !syncing) {
                    Text(
                        pluralStringResource(R.plurals.settings_account_stale, state.sync.stale, state.sync.stale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

@Composable
private fun SwitchAccountDialog(switch: PendingSwitch, onChoose: (SwitchChoice) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_account_switch_title, switch.toEmail)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    switch.fromEmail?.let { stringResource(R.string.settings_account_switch_body, it, switch.toEmail) }
                        ?: stringResource(R.string.settings_account_switch_body_unknown, switch.toEmail),
                )
                Text(stringResource(R.string.settings_account_switch_replace_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        // Three choices don't fit the usual confirm/dismiss pair; stacked, the longest label decides the width.
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onChoose(SwitchChoice.Upload) }) { Text(stringResource(R.string.settings_account_switch_upload)) }
                TextButton(
                    onClick = { onChoose(SwitchChoice.Replace) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_account_switch_replace)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_account_switch_cancel)) }
            }
        },
    )
}

internal val SignInException.Reason.messageRes: Int
    get() = when (this) {
        SignInException.Reason.NoServer -> R.string.settings_account_error_no_server
        SignInException.Reason.InvalidAddress -> R.string.settings_account_error_invalid_address
        SignInException.Reason.CleartextNotAllowed -> R.string.settings_account_error_https_only
        SignInException.Reason.Unreachable -> R.string.settings_account_error_unreachable
        SignInException.Reason.NotPocketBase -> R.string.settings_account_error_not_pocketbase
        SignInException.Reason.WrongCredentials -> R.string.settings_account_error_credentials
        SignInException.Reason.TooManyAttempts -> R.string.settings_account_error_too_many
        SignInException.Reason.ServerError -> R.string.settings_account_error_server
    }

private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** Android 17, where ACCESS_LOCAL_NETWORK starts. Earlier versions refuse it without a prompt, as an unknown permission. */
private const val LOCAL_NETWORK_SDK = 37

/**
 * Whether [url]'s server is on the local network, which Android 17 lets apps reach only with the
 * user's permission. Names are resolved, so `nas.lan` or a public name pointing at a 192.168.x
 * address count too; mDNS names and localhost are decided by name, as they may not resolve
 * without the permission. Resolving can take a moment, so it's done off the main thread.
 */
internal suspend fun needsLocalNetwork(url: String): Boolean {
    val host = hostOf(url) ?: return false
    return host == "localhost" || host.endsWith(".local") || withContext(Dispatchers.IO) { resolvesToLocalNetwork(host) }
}

private fun resolvesToLocalNetwork(host: String): Boolean = try {
    InetAddress.getAllByName(host).any(::isLocalNetwork)
} catch (_: UnknownHostException) {
    false // sign-in reports the server as unreachable
} catch (_: SecurityException) {
    false
}

/** The host of a server address as typed (scheme optional), without the brackets around an IPv6 literal. */
internal fun hostOf(url: String): String? = try {
    URI(url.trim().let { if ("://" in it) it else "https://$it" }).host?.removeSurrounding("[", "]")?.lowercase()?.ifEmpty { null }
} catch (_: URISyntaxException) {
    null
}

/** Loopback, private (RFC 1918), link-local, and IPv6 unique local (fc00::/7) addresses. */
internal fun isLocalNetwork(address: InetAddress): Boolean = address.isLoopbackAddress || address.isSiteLocalAddress ||
    address.isLinkLocalAddress || (address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC)

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
