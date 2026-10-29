package com.klaypeter.halt.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.klaypeter.halt.MainActivity
import com.klaypeter.halt.R
import com.klaypeter.halt.enforcement.GuardNotifications

/**
 * 两类通知：
 *  - 进度通知（常驻、静音、带进度条）：正在用被管控的 App 时一直挂着
 *  - 提醒通知（高优先级、横幅弹出）：到点、快用完时弹出来
 */
class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private var nextId = 1000

    private val openAppIntent: PendingIntent by lazy {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_QUOTA)
        PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    GuardNotifications.CHANNEL_ALERT,
                    context.getString(R.string.notif_channel_guard),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_desc)
                    enableVibration(true)
                }
            )
            nm?.createNotificationChannel(
                NotificationChannel(
                    GuardNotifications.CHANNEL_PROGRESS,
                    "额度进度",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "正在使用被管控应用时显示的剩余时间"
                    setShowBadge(false)
                }
            )
        }
    }

    /** 没在用被管控应用时的常驻状态通知，让「守护中」一直可见。 */
    fun showIdle(text: String) {
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, GuardNotifications.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("打住 · 守护中")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .build()
        runCatching { manager.notify(GuardNotifications.ID_PROGRESS, notification) }
    }

    /** 常驻进度通知。每秒刷新也不会响。 */
    fun showProgress(title: String, text: String, percent: Int) {
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, GuardNotifications.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .build()
        runCatching { manager.notify(GuardNotifications.ID_PROGRESS, notification) }
    }

    /** 高优先级横幅提醒，带「去重置」按钮。 */
    fun alert(title: String, text: String) {
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, GuardNotifications.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openAppIntent)
            .addAction(0, "去重置", openAppIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(nextId++, notification) }
            .onFailure { android.util.Log.w("Notifier", "notify 失败: $title", it) }
    }
}
