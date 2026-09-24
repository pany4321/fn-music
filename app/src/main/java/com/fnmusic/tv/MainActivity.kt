package com.fnmusic.tv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fnmusic.tv.ui.FnMusicApp
import com.fnmusic.tv.update.UpdateEffect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var isExiting = false
    private val appContainer: AppContainer get() = (application as TvMusicApplication).container

    // Denial only hides the system "now playing" entry; playback and login stay functional.
    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val container = appContainer
        setContent {
            FnMusicApp(container) { exitApplication(container) }
        }
        lifecycleScope.launch {
            container.updateCoordinator.effects.collectLatest { effect ->
                when (effect) {
                    is UpdateEffect.LaunchIntent -> runCatching { startActivity(effect.intent) }
                        .onFailure { container.updateCoordinator.handleIntentLaunchFailure() }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appContainer.updateCoordinator.onForeground()
    }

    override fun onResume() {
        super.onResume()
        appContainer.updateCoordinator.onResumeFromSystem()
    }

    override fun onStop() {
        appContainer.updateCoordinator.onBackground(isChangingConfigurations)
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun exitApplication(container: AppContainer) {
        if (isExiting) return
        // Car-unit opt-in: the confirmed Home back gesture backgrounds the task and
        // keeps playback running instead of taking the documented TV kill-path.
        if (container.appPreferences.backgroundBackExit.value) {
            moveTaskToBack(true)
            return
        }
        isExiting = true
        lifecycleScope.launch {
            try {
                container.shutdownForExit()
            } finally {
                finishAndRemoveTask()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}
