package com.klaypeter.halt.guard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.klaypeter.halt.data.AppDatabase
import com.klaypeter.halt.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 定期自检：无障碍服务是不是还活着。
 *
 * 国产 ROM 会杀掉或者直接关掉无障碍服务，用户完全无感 —— 额度看起来还在，
 * 其实早就拦不住了。这个 Worker 每 15 分钟检查一次，发现掉了就发通知。
 */
class GuardHealthWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        val hasAllowance = withContext(Dispatchers.IO) {
            runCatching { AppDatabase.get(context).allowanceDao().count() }.getOrDefault(0) > 0
        }
        if (!hasAllowance) return Result.success()

        // 没有「使用情况访问」就算不出用了多久，额度会永远不生效
        val usageOk = withContext(Dispatchers.IO) {
            runCatching { UsageRepository(context).hasUsageAccess() }.getOrDefault(true)
        }
        if (!usageOk) {
            Notifier(context).alert(
                "使用情况访问被关了",
                "没有它 Halt 算不出用了多久，额度不会生效。点一下重新开启。"
            )
            return Result.success()
        }

        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return Result.success()
        val running = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }

        if (!running) {
            // 一小时最多提醒一次，别刷屏
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastAlert = prefs.getLong(KEY_LAST_ALERT, 0L)
            val now = System.currentTimeMillis()
            if (now - lastAlert > 60 * 60_000L) {
                prefs.edit().putLong(KEY_LAST_ALERT, now).apply()
                Notifier(context).alert(
                    "Halt 的守护已经停止",
                    "无障碍服务被关闭了（通常是系统省电策略干的），额度现在不会生效。点一下重新开启。"
                )
            }
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_LAST_ALERT).apply()
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "guard-health"
        private const val PREFS = "guard_health"
        private const val KEY_LAST_ALERT = "last_alert"
    }
}
