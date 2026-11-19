package com.suibian.sunfile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SunColors {
    val Bg = Color(0xFF0F1115)
    val Panel = Color(0xFF161B22)
    val PanelAlt = Color(0xFF1C232D)
    val Divider = Color(0xFF252D38)
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)
    val TextDim = Color(0xFF616B77)
    val Accent = Color(0xFF2F81F7)
    val AccentSoft = Color(0x332F81F7)
    val Folder = Color(0xFFE3B341)
    val File = Color(0xFF7D8590)
    val Success = Color(0xFF3FB950)
    val Danger = Color(0xFFF85149)
}

@Composable
fun SunFileTheme(content: @Composable () -> Unit) {
    // 双栏文件管理器始终使用深色主题，贴近 MT 管理器的视觉习惯
    val scheme = darkColorScheme(
        primary = SunColors.Accent,
        onPrimary = Color.White,
        background = SunColors.Bg,
        onBackground = SunColors.TextPrimary,
        surface = SunColors.Panel,
        onSurface = SunColors.TextPrimary,
        surfaceVariant = SunColors.PanelAlt,
        onSurfaceVariant = SunColors.TextSecondary,
        error = SunColors.Danger
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun rememberIsDark(): Boolean = isSystemInDarkTheme()
