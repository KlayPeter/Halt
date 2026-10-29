package com.klaypeter.halt.enforcement

import com.klaypeter.halt.data.Allowance

enum class BlockReason {
    /** 额度已经用完了，这次是重新点进来。 */
    EXHAUSTED,

    /** 到点之后又玩了超过宽限时间。 */
    GRACE_EXPIRED
}

sealed interface Decision {
    data object Allow : Decision

    data class Block(
        val packageName: String,
        val reason: BlockReason,
        val usedMs: Long,
        val budgetMs: Long
    ) : Decision
}

/**
 * 判断一次「该不该拦」需要的全部输入。
 * 刻意不包含任何 Android 对象，这样规则可以单元测试。
 */
data class GuardInput(
    val foregroundPackage: String?,
    val allowances: Map<String, Allowance>,
    /** SystemClock.elapsedRealtime()，单调时钟，改系统时间也绕不过。 */
    val nowElapsedMs: Long,
    /** 当前这次使用开始的时间；null 表示还没开始计时（刚点进来）。 */
    val sessionStartElapsedMs: Long?,
    /** 自己、系统 UI、输入法这些不该算「切换了应用」。 */
    val ignoredPackages: Set<String> = emptySet(),
    /** 拨号这类永远不能拦。 */
    val neverBlockPackages: Set<String> = emptySet()
)

/**
 * 整个拦截策略就是一个纯函数。
 *
 * 拆出来的好处：规则能写单元测试，GuardService 只负责「喂输入、执行输出」，
 * 不会出现「逻辑散在几十个回调里、改一个条件要通读整个服务」的情况。
 */
object Enforcer {

    /** 到点之后再宽限 5 分钟，然后强退。 */
    const val GRACE_MS = 5 * 60_000L

    const val WARN_BEFORE_MS = 5 * 60_000L

    fun decide(input: GuardInput): Decision {
        val pkg = input.foregroundPackage ?: return Decision.Allow
        if (pkg in input.ignoredPackages || pkg in input.neverBlockPackages) return Decision.Allow

        val allowance = input.allowances[pkg] ?: return Decision.Allow
        val budget = allowance.budgetMs
        if (budget <= 0L) return Decision.Allow

        val used = usedMs(input, allowance)

        return when {
            // 正在用，且已经超过宽限 → 强退
            used >= budget + GRACE_MS ->
                Decision.Block(pkg, BlockReason.GRACE_EXPIRED, used, budget)

            // 没在计时却已经超额 → 说明是重新点进来的，直接拦
            input.sessionStartElapsedMs == null && used >= budget ->
                Decision.Block(pkg, BlockReason.EXHAUSTED, used, budget)

            else -> Decision.Allow
        }
    }

    /** 已用时长 = 库里累计的 + 本次会话至今。 */
    fun usedMs(input: GuardInput, allowance: Allowance): Long {
        val sessionMs = input.sessionStartElapsedMs
            ?.let { (input.nowElapsedMs - it).coerceAtLeast(0L) }
            ?: 0L
        return allowance.usedMs + sessionMs
    }
}
