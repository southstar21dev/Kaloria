package dev.southstar21.kaloria.health

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.R
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.designsystem.component.GlassBackdrop
import dev.southstar21.kaloria.core.designsystem.component.GlassStyle
import dev.southstar21.kaloria.core.designsystem.component.GreetingHeader
import dev.southstar21.kaloria.core.designsystem.component.SectionHeader
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.theme.KaloriaTheme

/**
 * What Health Connect opens when the user asks why Kaloria wants access to steps and weight (its
 * "privacy policy" link). Reached only through the manifest's permission-usage alias.
 */
class HealthPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            KaloriaTheme {
                GlassBackdrop { HealthPermissionsScreen(onBack = ::finish) }
            }
        }
    }
}

@Composable
internal fun HealthPermissionsScreen(onBack: () -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // A 48dp touch target around the 44dp glass button, as in Settings.
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Box(Modifier.size(44.dp).glass(CircleShape, GlassStyle.Card), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.health_permissions_back), Modifier.size(20.dp))
                    }
                }
                GreetingHeader(stringResource(R.string.health_permissions_title), stringResource(R.string.health_permissions_subtitle))
            }
        }
        item(key = "access-header") { SectionHeader(stringResource(R.string.health_permissions_access_header)) }
        item(key = "steps") { Reason(R.string.health_permissions_steps_title, R.string.health_permissions_steps_body) }
        item(key = "weight") { Reason(R.string.health_permissions_weight_title, R.string.health_permissions_weight_body) }
        item(key = "history") { Reason(R.string.health_permissions_history_title, R.string.health_permissions_history_body) }
        item(key = "privacy-header") { SectionHeader(stringResource(R.string.health_permissions_privacy_header)) }
        item(key = "stored") { Reason(R.string.health_permissions_stored_title, R.string.health_permissions_stored_body) }
        item(key = "control") { Reason(R.string.health_permissions_control_title, R.string.health_permissions_control_body) }
    }
}

@Composable
private fun Reason(@StringRes title: Int, @StringRes body: Int) {
    ContentCard(Modifier.fillMaxWidth()) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
