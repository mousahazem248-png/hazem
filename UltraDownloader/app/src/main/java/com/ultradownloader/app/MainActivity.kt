package com.ultradownloader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ultradownloader.app.ui.navigation.UltraAppRoot
import com.ultradownloader.app.ui.theme.UltraDownloaderTheme
import com.ultradownloader.app.ui.viewmodel.UltraMainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: UltraMainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingShareIntent(intent)

        setContent {
            val settings by mainViewModel.settingsState.collectAsState()
            UltraDownloaderTheme(
                themePreference = settings.theme,
                language = settings.language
            ) {
                UltraAppRoot(viewModel = mainViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedText.isNullOrBlank()) {
                // Extract first https:// URL from shared text if present
                val extractedUrl = Regex("""https://[^\s"]+""").find(sharedText)?.value ?: sharedText
                mainViewModel.onUrlInputChanged(extractedUrl)
            }
        }
    }
}
