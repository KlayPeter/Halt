package com.klaypeter.halt.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/** 一行统计结果。 */
data class AppUsage(
    val packageName: String,
    val label: String,
    val totalMs: Long,
    val launches: Int
)

data class DayUsage(
    val dayStart: Long,
    val totalMs: Long,
    val apps: List<AppUsage>
)

/**
 * 所有和「使用情况数据」打交道的事情都在这里：
 * 权限检查、跳授权页、把事件流还原成会话、按天聚合。
 */
class UsageRepository(private val context: Context) {

    // UsageEvents.Event 里的常量是 API 29 才改名成 RESUMED/PAUSED 的，
    // 但数值没变。直接用数字，避免低版本上触发 NewApi 检查。
    private companion object {
        const val EV_RESUMED = 1    // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
        const val EV_PAUSED = 2     // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
        const val EV_STOPPED = 23   // ACTIVITY_STOPPED（API 29+ 才有）
        const val MIN_SESSION_MS = 1_000L
    }

    /**
     * 是否已获得「使用情况访问」授权。
     *
     * 主判断用 AppOps；但部分 ROM 上 AppOps 状态更新不即时，
     * 所以再加一个「真去查一次，能查到数据就算已授权」的兜底。
     */
    fun hasUsageAccess(): Boolean {
        if (appOpsAllowed()) return true
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60 * 60 * 1000L, now)
                .orEmpty()
                .isNotEmpty()
        }.getOrDefault(false)
    }

    private fun appOpsAllowed(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 这个权限没法弹窗申请，只能把用户送到系统设置页。 */
    fun openPermissionSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 今天 0 点的时间戳（按设备本地时区）。 */
    fun startOfToday(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * 拉「今天 0 点 → 现在」的用量。
     * 会阻塞，请在 IO 线程调用。
     */
    fun loadToday(now: Long = System.currentTimeMillis()): DayUsage {
        val dayStart = startOfToday(now)
        if (!hasUsageAccess()) return DayUsage(dayStart, 0, emptyList())

        val sessions = querySessions(dayStart, now)
        val homePackage = resolveHomePackage()
        val self = context.packageName

        val totalByPackage = HashMap<String, Long>()
        val launches = HashMap<String, Int>()
        for (s in sessions) {
            if (s.packageName == self) continue          // 自己不统计
            if (s.packageName == "android") continue     // 系统内部
            if (s.packageName == homePackage) continue   // 桌面不算使用时长
            totalByPackage[s.packageName] =
                (totalByPackage[s.packageName] ?: 0L) + s.durationMs
            if (s.isLaunch) {
                launches[s.packageName] = (launches[s.packageName] ?: 0) + 1
            }
        }

        val apps = totalByPackage
            .filterValues { it >= MIN_SESSION_MS }
            .map { (pkg, total) ->
                AppUsage(
                    packageName = pkg,
                    label = labelOf(pkg),
                    totalMs = total,
                    launches = launches[pkg] ?: 0
                )
            }
            .sortedByDescending { it.totalMs }

        return DayUsage(dayStart, apps.sumOf { it.totalMs }, apps)
    }

    /**
     * 今天每个应用用了多久（包名 -> 毫秒）。
     * 「今日」页和「额度」页都读它，保证口径一致。
     */
    fun todayUsageByPackage(now: Long = System.currentTimeMillis()): Map<String, Long> =
        runCatching { loadToday(now).apps.associate { it.packageName to it.totalMs } }
            .getOrDefault(emptyMap())

    private data class Session(
        val packageName: String,
        val start: Long,
        val end: Long,
        /** 这段会话是不是「从别的应用切进来」的第一次进入。 */
        val isLaunch: Boolean
    ) {
        val durationMs: Long get() = end - start
    }

    /**
     * 把系统事件流还原成「一次前台停留」。
     *
     * RESUMED 开一个会话，PAUSED/STOPPED 关掉它；到 now 还没关的就是正在用的应用。
     *
     * 「打开次数」只在**前台应用换人**时 +1：
     * 同一个 App 内部从一个页面跳到另一个页面，也会产生 PAUSED + RESUMED，
     * 但那是一次使用而不是一次打开，所以用 lastResumed 过滤掉。
     */
    private fun querySessions(from: Long, to: Long): List<Session> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events: UsageEvents = usm.queryEvents(from, to) ?: return emptyList()

        val opened = HashMap<String, Long>()
        val openedIsLaunch = HashMap<String, Boolean>()
        var lastResumed: String? = null
        val sessions = ArrayList<Session>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                EV_RESUMED -> {
                    val fromOtherApp = pkg != lastResumed
                    if (!opened.containsKey(pkg)) {
                        opened[pkg] = event.timeStamp
                        openedIsLaunch[pkg] = fromOtherApp
                    }
                    lastResumed = pkg
                }

                EV_PAUSED, EV_STOPPED -> {
                    val start = opened.remove(pkg) ?: continue
                    val isLaunch = openedIsLaunch.remove(pkg) ?: false
                    if (event.timeStamp > start) {
                        sessions += Session(pkg, start, event.timeStamp, isLaunch)
                    }
                }
            }
        }

        val end = minOf(to, System.currentTimeMillis())
        opened.forEach { (pkg, start) ->
            if (end > start) {
                sessions += Session(pkg, start, end, openedIsLaunch[pkg] ?: false)
            }
        }
        return sessions
    }

    /** 当前桌面（Launcher）的包名。桌面停留不算「使用手机」。 */
    private fun resolveHomePackage(): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, 0)
            }?.activityInfo?.packageName
        }.getOrNull()
    }

    private fun labelOf(packageName: String): String = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}