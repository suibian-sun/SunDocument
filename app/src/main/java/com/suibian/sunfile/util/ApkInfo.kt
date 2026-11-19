package com.suibian.sunfile.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import java.io.File
import androidx.core.graphics.drawable.toBitmap

data class ApkMeta(
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val activities: Int,
    val icon: Bitmap?
)

/** 读取 APK 的清单信息，用于在文件管理器内直接查看安装包详情 */
object ApkInspector {

    fun read(context: Context, file: File): ApkMeta? = runCatching {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_ACTIVITIES or
            (if (Build.VERSION.SDK_INT >= 33) PackageManager.GET_SIGNING_CERTIFICATES else 0)
        val info: PackageInfo = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        @Suppress("DEPRECATION")
        val app = info.applicationInfo
        @Suppress("DEPRECATION")
        val label = app?.let {
            it.sourceDir = file.absolutePath
            it.publicSourceDir = file.absolutePath
            runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
        } ?: file.name
        val icon = app?.let {
            runCatching { pm.getApplicationIcon(it).toBitmap(144, 144) }.getOrNull()
        }
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        ApkMeta(
            label = label,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = code,
            minSdk = app?.minSdkVersion ?: 0,
            targetSdk = app?.targetSdkVersion ?: 0,
            permissions = info.requestedPermissions?.toList() ?: emptyList(),
            activities = info.activities?.size ?: 0,
            icon = icon
        )
    }.getOrNull()
}

fun Drawable?.asBitmap(): Bitmap? = when (this) {
    null -> null
    is BitmapDrawable -> bitmap
    else -> runCatching { toBitmap(144, 144) }.getOrNull()
}
