package com.klaypeter.halt.guard

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * 检测系统时间被改动。
 *
 * 为什么需要：额度按「今天」的 UsageStats 算，而「今天」是按系统墙钟分的。
 * 把日期改到明天，「今天已用」就归零，额度直接回满 —— 这是最容易想到的作弊手段。
 *
 * 做法：墙钟和单调时钟应该同步走。两者的差值突然跳变，就说明有人动了时间。
 * 单调时钟在设备重启前不会被改，所以它是个可靠的参照。
 */
object ClockGuard {

    private const val PREFS = "clock_guard"
    private const val KEY_WALL = "last_wall"
    private const val KEY_ELAPSED = "last_elapsed"
    private const val TOLERANCE_MS = 60_000L

    /** 返回 true 表示这次检测到时间被改过。 */
    fun check(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastWall = prefs.getLong(KEY_WALL, 0L)
        val lastElapsed = prefs.getLong(KEY_ELAPSED, 0L)
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()

        prefs.edit().putLong(KEY_WALL, now).putLong(KEY_ELAPSED, elapsed).apply()

        // 第一次运行，或设备重启过（elapsed 变小），不判断
        if (lastWall <= 0L || lastElapsed <= 0L) return false
        if (elapsed < lastElapsed) return false

        val wallDelta = now - lastWall
        val monotonicDelta = elapsed - lastElapsed
        val skew = kotlin.math.abs(wallDelta - monotonicDelta)

        if (skew > TOLERANCE_MS) {
            Log.w(
                "ClockGuard",
                "系统时间被改了：墙钟走了 ${wallDelta}ms，单调时钟走了 ${monotonicDelta}ms"
            )
            return true
        }
        return false
    }
}