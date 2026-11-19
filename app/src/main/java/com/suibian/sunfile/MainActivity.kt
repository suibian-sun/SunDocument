package com.suibian.sunfile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suibian.sunfile.data.PrefsStore
import com.suibian.sunfile.ui.MainScreen
import com.suibian.sunfile.ui.theme.SunDocumentTheme
import com.suibian.sunfile.util.Permissions

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        val activity = this
        val prefsStore = PrefsStore.get(activity)
        setContent {
            val prefs by prefsStore.prefs.collectAsStateWithLifecycle()
            val dark = prefs.themeMode.isDark(androidx.compose.foundation.isSystemInDarkTheme())

            // 状态栏图标随主题反色，避免浅色下白字看不见
            SideEffect {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .isAppearanceLightStatusBars = !dark
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .isAppearanceLightNavigationBars = !dark
            }

            SunDocumentTheme(mode = prefs.themeMode) {
                var hasAccess by remember { mutableStateOf(Permissions.hasAllFilesAccess(activity)) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasAccess = Permissions.hasAllFilesAccess(activity)
                }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    hasAccess = Permissions.hasAllFilesAccess(activity)
                }

                LaunchedEffect(Unit) {
                    if (!hasAccess && Permissions.needsRuntimeStoragePermission()) {
                        permissionLauncher.launch(Permissions.runtimeStoragePermissions())
                    }
                }

                MainScreen(
                    hasAccess = hasAccess,
                    onRequestAccess = {
                        if (Permissions.needsRuntimeStoragePermission()) {
                            permissionLauncher.launch(Permissions.runtimeStoragePermissions())
                        } else {
                            Permissions.openAllFilesAccessSettings(activity)
                        }
                    }
                )
            }
        }
    }
}
