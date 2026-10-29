package com.klaypeter.halt.guard

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.klaypeter.halt.MainActivity
import com.klaypeter.halt.data.Allowance
import com.klaypeter.halt.data.GuardRepository
import com.klaypeter.halt.enforcement.BlockReason
import com.klaypeter.halt.enforcement.Decision
import com.klaypeter.halt.enforcement.Enforcer
import com.klaypeter.halt.enforcement.GuardInput
import com.klaypeter.halt.enforcement.GuardianService
import com.klaypeter.halt.util.formatClock
import com.klaypeter.halt.util.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 额度守护。
 *
 * 结构：
 *   - 判断「该不该拦」全部交给 [Enforcer]（纯函数、可测试）
 *   - 这个类只负责：喂输入 → 执行决策（遮罩/踢人/通知）
 *   - 计时用 elapsedRealtime；会话开始时间落盘，进程被杀后能补记
 *   - 5 秒一次的兜底轮询，服务刚起来或事件漏了也能接上
 */
class GuardService : AccessibilityService() {

    private companion object {
        const val TAG = "GuardService"
        const val SYSTEM_UI = "com.android.systemui"
        const val KICK_DELAY_MS = 500L
        const val HEALTH_POLL_MS = 5_000L

        const val PREFS = "guard_state"
        const val KEY_PENDING_PKG = "pending_pkg"
        const val KEY_PENDING_WALL = "pending_wall"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var repository: GuardRepository
    private lateinit var notifier: Notifier
    private lateinit var overlay: OverlayController
    private lateinit var banner: BannerOverlay

    private var allowances: Map<String, Allowance> = emptyMap()

    /** 自己、系统 UI、输入法这些不算「切换了应用」。 */
    private val ignoredPackages = mutableSetOf<String>()

    /** 拨号这类永远不能拦，避免把自己锁在紧急情况外。 */
    private val neverBlockPackages = mutableSetOf<String>()

    private var lastForeground: String? = null
    private var session: Session? = null
    private var ticker: Job? = null

    private data class Session(
        val packageName: String,
        val startElapsed: Long,
        var warned: Boolean = false,
        var timeUpNotified: Boolean = false
    )

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = endSession()
    }

    /** 解锁后立刻复核一次，避免从锁屏回来时事件还没到。 */
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            scope.launch {
                val pkg = withContext(Dispatchers.IO) { currentForegroundPackage() } ?: return@launch
                Log.d(TAG, "解锁后复核: $pkg")
                onForegroundChanged(pkg)
            }
        }
    }

    // ---------------------------------------------------------------- 生命周期

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = GuardRepository(applicationContext)
        notifier = Notifier(this)
        overlay = OverlayController(this)
        banner = BannerOverlay(this)

        // 自己弹的遮罩/横幅也会产生窗口事件
        ignoredPackages += packageName
        ignoredPackages += SYSTEM_UI
        // 所有输入法都忽略，而不是只取默认那个
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.enabledInputMethodList
            ?.forEach { info -> info.packageName?.let { ignoredPackages += it } }

        (getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
            ?.defaultDialerPackage
            ?.let { neverBlockPackages += it }

        restorePendingSession()
        GuardianService.start(this)

        scope.launch {
            repository.observeAll().collectLatest { list ->
                allowances = list.associateBy { it.packageName }
                Log.d(TAG, "额度已加载: ${list.map { "${it.label}=${it.usedMs}/${it.budgetMs}" }}")
                if (session == null) notifier.showIdle(idleText())
            }
        }

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))

        startHealthPoll()
        Log.d(TAG, "onServiceConnected 完成")
    }

    /**
     * 兜底轮询：无障碍事件漏了、或者服务刚被重连时，
     * 靠自己查一次当前前台应用把状态接回来。
     */
    private fun startHealthPoll() {
        scope.launch {
            while (isActive) {
                delay(HEALTH_POLL_MS)
                try {
                    if (session == null) {
                        val pkg = withContext(Dispatchers.IO) { currentForegroundPackage() }
                        if (pkg != null && pkg != lastForeground) {
                            Log.d(TAG, "轮询发现前台: $pkg")
                            onForegroundChanged(pkg)
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "轮询失败", t)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handleEvent(event)
        } catch (t: Throwable) {
            // 单个事件出错不能让服务挂掉，否则系统会直接停用无障碍
            Log.e(TAG, "处理事件失败", t)
        }
    }

    private fun handleEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignoredPackages) return
        Log.v(TAG, "前台事件 $pkg")
        onForegroundChanged(pkg)
    }

    override fun onInterrupt() = endSession()

    override fun onDestroy() {
        endSession()
        runCatching { unregisterReceiver(screenOffReceiver) }
        runCatching { unregisterReceiver(userPresentReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 决策

    private fun buildInput(pkg: String?, sessionStart: Long?): GuardInput = GuardInput(
        foregroundPackage = pkg,
        allowances = allowances,
        nowElapsedMs = SystemClock.elapsedRealtime(),
        sessionStartElapsedMs = sessionStart,
        ignoredPackages = ignoredPackages,
        neverBlockPackages = neverBlockPackages
    )

    private fun onForegroundChanged(pkg: String) {
        lastForeground = pkg
        if (pkg == session?.packageName) return
        endSession()

        val allowance = allowances[pkg] ?: return
        val decision = Enforcer.decide(buildInput(pkg, sessionStart = null))
        Log.d(TAG, "进入 $pkg used=${allowance.usedMs}/${allowance.budgetMs} decision=$decision")

        if (decision is Decision.Block) {
            block(allowance, decision)
            return
        }

        session = Session(pkg, SystemClock.elapsedRealtime())
        persistPendingSession(pkg)
        banner.resetForSession()
        notifier.showProgress(
            title = "${allowance.label} · 剩余",
            text = formatClock(allowance.remainingMs),
            percent = percentOf(allowance.usedMs, allowance.budgetMs)
        )
        startTicker()
    }

    // ---------------------------------------------------------------- 计时

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1_000)
                try {
                    tick()
                } catch (t: Throwable) {
                    Log.e(TAG, "tick 失败", t)
                }
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun tick() {
        val current = session ?: return
        val allowance = allowances[current.packageName] ?: return

        val input = buildInput(current.packageName, sessionStart = current.startElapsed)
        val used = Enforcer.usedMs(input, allowance)
        val budget = allowance.budgetMs
        val remaining = budget - used

        notifier.showProgress(
            title = "${allowance.label} · ${if (remaining >= 0) "剩余" else "已超出"}",
            text = if (remaining >= 0) {
                formatClock(remaining)
            } else {
                formatClock(used - budget) + "（宽限中）"
            },
            percent = percentOf(used, budget)
        )

        // 挂着被管控应用时横幅常驻，划掉后本次不再弹
        if (!banner.dismissed) {
            val untilExit = if (remaining >= 0) remaining else (budget + Enforcer.GRACE_MS) - used
            if (!banner.isShowing) {
                banner.show(allowance.label) { openQuotaTab() }
            }
            banner.setHeader(
                if (remaining >= 0) "${allowance.label} · 剩余"
                else "${allowance.label} · 宽限"
            )
            banner.update(untilExit, used.toFloat() / budget)
        }

        val decision = Enforcer.decide(input)
        when {
            decision is Decision.Block -> {
                endSession()
                block(allowance, decision)
            }

            used >= budget -> {
                if (!current.timeUpNotified) {
                    current.timeUpNotified = true
                    notifier.alert(
                        "${allowance.label} 时间到了",
                        "已用 ${formatDuration(used)}。再玩 5 分钟就会被强制退出。"
                    )
                }
            }

            used >= budget - Enforcer.WARN_BEFORE_MS -> {
                if (budget > Enforcer.WARN_BEFORE_MS && !current.warned) {
                    current.warned = true
                    notifier.alert(
                        "${allowance.label} 快用完了",
                        "还剩 ${formatDuration(budget - used)}。"
                    )
                }
            }
        }
    }

    /** 结算当前这一段，写回数据库。 */
    private fun endSession() {
        val current = session ?: return
        session = null
        stopTicker()
        banner.hide()
        notifier.showIdle(idleText())
        clearPendingSession()

        val elapsed = SystemClock.elapsedRealtime() - current.startElapsed
        Log.d(TAG, "end session ${current.packageName} +${elapsed}ms")
        if (elapsed <= 0) return

        val allowance = allowances[current.packageName] ?: return
        allowances = allowances + (current.packageName to allowance.copy(usedMs = allowance.usedMs + elapsed))
        scope.launch { repository.addUsed(current.packageName, elapsed) }
    }

    // ---------------------------------------------------------------- 会话落盘

    private fun persistPendingSession(pkg: String) {
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PENDING_PKG, pkg)
            .putLong(KEY_PENDING_WALL, System.currentTimeMillis())
            .apply()
    }

    private fun clearPendingSession() {
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_PENDING_PKG)
            .remove(KEY_PENDING_WALL)
            .apply()
    }

    /**
     * 进程被杀之前没来得及结算的那一段，用时间戳补回来。
     * 最多补 1 小时，避免关机几天后一次性加爆。
     */
    private fun restorePendingSession() {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pkg = prefs.getString(KEY_PENDING_PKG, null) ?: return
        val wall = prefs.getLong(KEY_PENDING_WALL, 0L)
        clearPendingSession()
        if (wall <= 0L) return

        val delta = (System.currentTimeMillis() - wall).coerceIn(0L, 60 * 60_000L)
        if (delta < 5_000L) return
        Log.d(TAG, "补记未结算的会话 $pkg +${delta}ms")
        scope.launch { repository.addUsed(pkg, delta) }
    }

    // ---------------------------------------------------------------- 执行

    private fun block(allowance: Allowance, decision: Decision.Block) {
        val reason = when (decision.reason) {
            BlockReason.EXHAUSTED -> "额度用完了，先去做点别的吧"
            BlockReason.GRACE_EXPIRED -> "已经超出宽限时间，先离开一下"
        }
        Log.d(TAG, "block ${allowance.packageName} reason=${decision.reason}")

        banner.hide()
        notifier.showIdle(idleText())

        overlay.show(
            title = "${allowance.label} 的额度已用完",
            subtitle = "$reason\n\n已用 ${formatDuration(decision.usedMs)} / 已给 ${formatDuration(decision.budgetMs)}",
            actionLabel = "去重置额度",
            onAction = { openQuotaTab() },
            durationMs = 4_000
        )

        scope.launch {
            delay(KICK_DELAY_MS)
            val kicked = performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "performGlobalAction(HOME) = $kicked")
            killBackground(allowance.packageName)
            runCatching {
                Toast.makeText(this@GuardService, "${allowance.label} 额度已用完", Toast.LENGTH_LONG).show()
            }
            notifier.alert("${allowance.label} 已锁定", "额度用完，重置后才能继续使用。")
        }
    }

    /** 尽力把目标应用的后台进程回收。系统可能忽略，日志里能看到。 */
    private fun killBackground(packageName: String) {
        runCatching {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            Log.d(TAG, "killBackgroundProcesses($packageName) 已调用")
        }.onFailure { Log.w(TAG, "killBackgroundProcesses 失败", it) }
    }

    /** 当前前台应用（用于解锁复核和兜底轮询）。依赖「使用情况访问」权限。 */
    private fun currentForegroundPackage(): String? = runCatching {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 2 * 60_000L, now) ?: return null
        var pkg: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == 1) pkg = event.packageName
        }
        pkg
    }.getOrNull()

    private fun percentOf(usedMs: Long, budgetMs: Long): Int {
        if (budgetMs <= 0) return 0
        return ((usedMs.toFloat() / budgetMs) * 100).toInt().coerceIn(0, 100)
    }

    private fun idleText(): String {
        val guarded = allowances.values
        if (guarded.isEmpty()) return "还没有设置额度，去「额度」页加一个"
        val exhausted = guarded.count { it.exhausted }
        return if (exhausted > 0) {
            "${guarded.size} 个应用受控，$exhausted 个已用完"
        } else {
            "${guarded.size} 个应用受控"
        }
    }

    private fun openQuotaTab() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_QUOTA)
        )
    }
}
