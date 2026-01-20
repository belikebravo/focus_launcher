package com.example.focuslauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.focuslauncher.data.AppRepository
import com.example.focuslauncher.ui.home.HomeScreen
import com.example.focuslauncher.ui.home.HomeViewModel
import com.example.focuslauncher.ui.home.HomeViewModelFactory
import com.example.focuslauncher.ui.theme.FocusLauncherTheme

import com.example.focuslauncher.data.SettingsRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val appRepository = AppRepository(applicationContext)
        val settingsRepository = SettingsRepository(applicationContext)
        val factory = HomeViewModelFactory(appRepository, settingsRepository)
        val viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]

        setContent {
            FocusLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }
}
