package io.strlght.estee

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.strlght.estee.ui.settings.SettingsScreen
import io.strlght.estee.ui.settings.SettingsViewModel
import io.strlght.estee.ui.theme.EsteeTheme
import io.strlght.estee.ui.transcribe.TranscriptionScreen
import io.strlght.estee.ui.transcribe.TranscriptionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private val pendingShare = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            extractSharedUri(intent)
        }

        setContent {
            EsteeTheme {
                EsteeApp(
                    pendingShare = pendingShare,
                    onShareHandled = { pendingShare.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedUri(intent)
    }

    private fun extractSharedUri(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val uri = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri != null) {
            pendingShare.value = uri
        }
    }
}

private object NavRoutes {
    const val TRANSCRIBE = "transcribe"
    const val SETTINGS = "settings"
}

@Composable
fun EsteeApp(
    pendingShare: StateFlow<Uri?>,
    onShareHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val incomingUri by pendingShare.collectAsStateWithLifecycle()
    val transcriptionViewModel: TranscriptionViewModel = viewModel()

    LaunchedEffect(incomingUri) {
        incomingUri?.let { uri ->
            transcriptionViewModel.handleIncomingFile(uri)
            navController.navigate(NavRoutes.TRANSCRIBE) {
                popUpTo(NavRoutes.SETTINGS) { inclusive = true }
                launchSingleTop = true
            }
            onShareHandled()
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.TRANSCRIBE,
    ) {
        composable(NavRoutes.TRANSCRIBE) {
            TranscriptionScreen(
                viewModel = transcriptionViewModel,
                onOpenSettings = { navController.navigate(NavRoutes.SETTINGS) },
            )
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
