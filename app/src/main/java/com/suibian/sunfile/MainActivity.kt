package com.suibian.sunfile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.suibian.sunfile.ui.FileManagerScreen
import com.suibian.sunfile.ui.SunFileTheme
import com.suibian.sunfile.util.Permissions

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val activity = this
        setContent {
            SunFileTheme {
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

                FileManagerScreen(
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
