package dev.southstar21.kaloria

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import dev.southstar21.kaloria.navigation.Shortcut
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.repository.SettingsRepository
import dev.southstar21.kaloria.core.designsystem.theme.KaloriaTheme
import dev.southstar21.kaloria.navigation.KaloriaNavDisplay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val preferences = settings.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** A widget shortcut waiting to be opened by the navigation host. */
    private val shortcut = mutableStateOf<Shortcut?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.preferences.value == null }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) shortcut.value = Shortcut.from(intent)
        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            KaloriaTheme(
                reduceTransparency = preferences?.reduceTransparency ?: false,
            ) {
                KaloriaNavDisplay(shortcut = shortcut.value, onShortcutHandled = { shortcut.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Shortcut.from(intent)?.let { shortcut.value = it }
    }
}
