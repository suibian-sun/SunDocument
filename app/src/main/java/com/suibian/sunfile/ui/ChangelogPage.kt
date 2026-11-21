package com.suibian.sunfile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suibian.sunfile.BuildConfig
import com.suibian.sunfile.data.ChangeEntry
import com.suibian.sunfile.data.Changelog
import com.suibian.sunfile.ui.theme.Mt
import com.suibian.sunfile.ui.theme.SunColors

/** 设置 → 历史更新：每个版本一屏可读的改动清单 */
@Composable
fun ChangelogPage(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SunColors.Bg)
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(SunColors.TopBar)
                .statusBarsPadding()
                .height(Mt.BarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = SunColors.OnTopBar,
                    modifier = Modifier.size(21.dp)
                )
            }
            Text("历史更新", color = SunColors.OnTopBar, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }

        LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
            items(Changelog.entries, key = { it.version }) { entry ->
                VersionBlock(entry)
            }
        }
    }
}

@Composable
private fun VersionBlock(entry: ChangeEntry) {
    val current = entry.version == BuildConfig.VERSION_NAME
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "v${entry.version}",
                color = SunColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
            if (current) {
                Spacer(Modifier.width(7.dp))
                Text(
                    "当前",
                    color = SunColors.Accent,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SunColors.AccentSoft)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(entry.date, color = SunColors.TextTertiary, fontSize = 9.5.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text(entry.title, color = SunColors.TextSecondary, fontSize = 11.5.sp)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(com.suibian.sunfile.ui.theme.SunShapes.Small)
                .background(SunColors.Surface1)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            entry.items.forEach { item ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(SunColors.Accent)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        item,
                        color = SunColors.TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
