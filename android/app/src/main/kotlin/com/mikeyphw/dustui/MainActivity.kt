package com.mikeyphw.dustui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mikeyphw.dustui.ui.DustUiApp
import com.mikeyphw.dustui.ui.MainViewModel
import com.mikeyphw.dustui.ui.MainViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val app = application as DustUiApplication
        MainViewModelFactory(app.container.scanRepository)
    }

    private var pendingDeepLink by mutableStateOf<String?>(null)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLink = intent.semanticDeepLink()
        enableEdgeToEdge()
        setContent {
            DustUiApp(
                viewModel = viewModel,
                requestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                incomingDeepLink = pendingDeepLink,
                onDeepLinkConsumed = { pendingDeepLink = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = intent.semanticDeepLink()
    }

    private fun Intent.semanticDeepLink(): String? =
        takeIf { action == Intent.ACTION_VIEW }?.dataString
}
