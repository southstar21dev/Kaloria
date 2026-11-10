package dev.southstar21.kaloria

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.health.HealthWeightSync
import dev.southstar21.kaloria.core.data.repository.SettingsRepository
import dev.southstar21.kaloria.core.designsystem.theme.KaloriaTheme
import dev.southstar21.kaloria.core.ui.LocalClock
import dev.southstar21.kaloria.navigation.KaloriaNavDisplay
import dev.southstar21.kaloria.navigation.Shortcut
import dev.southstar21.kaloria.widget.WidgetShortcuts
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val preferences = settings.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** Weigh-ins both ways with Samsung Health. Only while in front: Health Connect refuses background reads. */
    @Inject lateinit var healthWeights: HealthWeightSync

    @Inject lateinit var clock: Clock

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { healthWeights.syncIfStale() }
    }

    /** A widget shortcut waiting to be opened by the navigation host. */
    private val shortcut = mutableStateOf<Shortcut?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.preferences.value == null }
        // Always the light silver theme, so the system bars always get dark icons.
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // Shortcuts come only through the widget's unexported trampoline, never from this exported
        // activity's intent, so other apps can't open screens over the user's work.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WidgetShortcuts.pending.filterNotNull().collect { Shortcut.from(WidgetShortcuts.take())?.let { shortcut.value = it } }
            }
        }
        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            KaloriaTheme(
                reduceTransparency = preferences?.reduceTransparency ?: false,
            ) {
                CompositionLocalProvider(LocalClock provides clock) {
                    KaloriaNavDisplay(shortcut = shortcut.value, onShortcutHandled = { shortcut.value = null })
                }
            }
        }
    }
}
