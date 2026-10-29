package com.klaypeter.halt.enforcement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.klaypeter.halt.MainActivity
import com.klaypeter.halt.R

/**
 * 常驻前台服务，唯一目的：把进程优先级抬高，别让系统把无障碍服务连带杀掉。
 *
 * 做法来自 Detoxwall 的 GuardianService：START_STICKY + specialUse 类型前台服务。
 * 通知 ID 和「守护中」那条共用，所以用户只会看到一条常驻通知。
 */
class GuardianService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, GuardNotifications.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("打住 · 守护中")
            .setContentText("额度和拦截正在生效")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()

        runCatching {
            ServiceCompat.startForeground(
                this,
                GuardNotifications.ID_PROGRESS,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
        }

        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(GuardNotifications.CHANNEL_PROGRESS) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                GuardNotifications.CHANNEL_PROGRESS,
                "额度进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "守护运行状态和使用中的剩余时间"
                setShowBadge(false)
            }
        )
    }

    companion object {
        /** 可以重复调用；系统拒绝时静默失败（后台限制）。 */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, GuardianService::class.java)
                )
            }
        }
    }
}
