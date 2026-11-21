package com.suibian.sunfile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suibian.sunfile.data.Mt2
import com.suibian.sunfile.data.PrefsStore
import com.suibian.sunfile.ui.AppBackdrop
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
        Mt2.installCrashHandler(activity)
        // MT2 目录放在内部存储根目录，卸载后数据仍在；不可写时自动退到应用专属目录
        kotlin.concurrent.thread(name = "mt2-init", isDaemon = true) {
            runCatching { Mt2.ensure(activity) }
            // 图标映射表提前解析，列表首帧就不用等
            runCatching { com.suibian.sunfile.data.IconTheme.ensureLoaded(activity) }
            // 回收站 30 天到期清理，放在启动线程里，不占首帧
            runCatching { com.suibian.sunfile.data.Trash.purgeExpired(activity) }
            // 上次开着 MCP 服务的话，随应用一起恢复监听
            runCatching { com.suibian.sunfile.data.Mcp.autoStart(activity) }
            // 通道恢复要起进程验证，放后台，别让首帧等它
            runCatching {
                val p = PrefsStore.get(activity).prefs.value
                com.suibian.sunfile.data.Shell.suCommand = p.customSuCommand.ifBlank { "su" }
                if (p.autoStartService) com.suibian.sunfile.data.Shell.initDefault()
            }
        }
        val prefsStore = PrefsStore.get(activity)
        setContent {
            val prefs by prefsStore.prefs.collectAsStateWithLifecycle()
            val dark = prefs.themeMode.isDark(androidx.compose.foundation.isSystemInDarkTheme())

            // 中性模板的顶栏恒为深灰，状态栏固定用亮色图标；
            // 二次元模板的浅色顶栏是浅玻璃，必须切成深色图标，否则白字压在浅底上直接消失
            val lightBars = prefs.skinId == "anime" && !dark
            SideEffect {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .isAppearanceLightStatusBars = lightBars
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .isAppearanceLightNavigationBars = !dark
            }

            SunDocumentTheme(mode = prefs.themeMode, accent = prefs.accentColor, skinId = prefs.skinId) {
                // 壁纸在页面之下，页面自身半透明，所以这层必须包住整个应用而不是某一页
                Box(Modifier.fillMaxSize()) {
                    AppBackdrop(
                        // 中性模板的色板不透明，壁纸看不见；不加载也就不会白耗流量
                        source = if (prefs.backdropEnabled && prefs.skinId == "anime") prefs.backdropSource else "",
                        bust = prefs.backdropBust,
                        blurDp = prefs.backdropBlur,
                        dimPercent = prefs.backdropDim
                    )
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
}
