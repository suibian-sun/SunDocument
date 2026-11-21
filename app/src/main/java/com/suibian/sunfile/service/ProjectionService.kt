package com.suibian.sunfile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.suibian.sunfile.data.ScreenSampler

/**
 * 屏幕取色的承载服务。
 *
 * 只做两件事：进入前台（满足 Android 14 的媒体投射要求）、把授权结果交给
 * ScreenSampler 取一帧，取完立即 stopSelf——不在后台常驻。
 */
class ProjectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(ID, notification)
        }

        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) {
            ScreenSampler.reset()
            stopSelf()
            return START_NOT_STICKY
        }
        ScreenSampler.capture(this, code, data)
        // 取样是 450ms 延迟的一次性动作，给足时间后自动收尾
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ stopSelf() }, 2500)
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "屏幕取色", NotificationManager.IMPORTANCE_MIN)
            )
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("正在截取屏幕")
            .setContentText("取色完成后自动关闭")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "sun_projection"
        private const val ID = 0x5A11
    }
}
