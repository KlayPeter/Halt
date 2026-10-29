package com.klaypeter.halt.guard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat

/**
 * 这个功能要跑起来需要的三个授权。
 * 都不是运行时弹窗能拿到的（通知除外），只能跳系统设置让用户手动开。
 */
object Permissions {

    /**
     * 是否已开启无障碍。
     *
     * 两种判断方式结果可能不一致（国产 ROM 上见过设置里写了但没真正绑定）：
     *  1. AccessibilityManager 的实时列表 —— 反映「系统真的绑上了没有」，最准
     *  2. Settings.Secure 里的字符串 —— 反映「用户勾了这个开关」
     * 任一为真就算开。
     */
    fun accessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (manager != null) {
            val live = runCatching {
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            }.getOrDefault(emptyList())
            if (live.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }) {
                return true
            }
        }

        val expected = ComponentName(context, GuardService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun overlayAllowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun notificationsAllowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openAccessibilitySettings(context: Context) = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openOverlaySettings(context: Context) = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openNotificationSettings(context: Context) = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
