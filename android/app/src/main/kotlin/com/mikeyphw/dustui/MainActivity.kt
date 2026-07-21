package com.mikeyphw.dustui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.mikeyphw.dustui.ui.DustUiApp
import com.mikeyphw.dustui.ui.MainViewModel
import com.mikeyphw.dustui.ui.MainViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val app = application as DustUiApplication
        MainViewModelFactory(app.container.scanRepository)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { DustUiApp(viewModel) }
    }
}
