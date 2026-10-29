package com.klaypeter.halt.enforcement

import com.klaypeter.halt.data.Allowance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拦截规则是纯函数，所以可以像这样直接测边界。
 * 这些用例覆盖的就是真机上最难复现的几种状态。
 */
class EnforcerTest {

    private val douyin = Allowance(
        packageName = "com.ss.android.ugc.aweme",
        label = "抖音",
        budgetMs = 20 * 60_000L,
        usedMs = 0L
    )

    private fun input(
        usedMs: Long = 0L,
        sessionStart: Long? = null,
        now: Long = 10_000_000L,
        pkg: String? = douyin.packageName
    ) = GuardInput(
        foregroundPackage = pkg,
        allowances = mapOf(douyin.packageName to douyin.copy(usedMs = usedMs)),
        nowElapsedMs = now,
        sessionStartElapsedMs = sessionStart
    )

    @Test
    fun `没设额度的应用放行`() {
        val decision = Enforcer.decide(input(pkg = "com.tencent.mm"))
        assertTrue(decision is Decision.Allow)
    }

    @Test
    fun `额度没用完放行`() {
        val decision = Enforcer.decide(
            input(usedMs = 5 * 60_000L, sessionStart = 10_000_000L - 60_000L)
        )
        assertTrue(decision is Decision.Allow)
    }

    @Test
    fun `已经用完再点进来直接拦`() {
        val decision = Enforcer.decide(
            input(usedMs = 20 * 60_000L, sessionStart = null)
        )
        assertTrue(decision is Decision.Block)
        assertEquals(BlockReason.EXHAUSTED, (decision as Decision.Block).reason)
    }

    @Test
    fun `正在用到点也立刻拦，不给宽限`() {
        val now = 10_000_000L
        val decision = Enforcer.decide(
            input(usedMs = 20 * 60_000L, sessionStart = now - 60_000L, now = now)
        )
        assertTrue(decision is Decision.Block)
        assertEquals(BlockReason.TIME_UP, (decision as Decision.Block).reason)
    }

    @Test
    fun `差一秒还放行`() {
        val now = 10_000_000L
        val decision = Enforcer.decide(
            input(usedMs = 20 * 60_000L - 2_000L, sessionStart = now - 1_000L, now = now)
        )
        assertTrue(decision is Decision.Allow)
    }

    @Test
    fun `自己或输入法这类忽略的包名放行`() {
        val decision = Enforcer.decide(
            GuardInput(
                foregroundPackage = douyin.packageName,
                allowances = mapOf(douyin.packageName to douyin.copy(usedMs = 99_000_000L)),
                nowElapsedMs = 1_000L,
                sessionStartElapsedMs = null,
                ignoredPackages = setOf(douyin.packageName)
            )
        )
        assertTrue(decision is Decision.Allow)
    }

    @Test
    fun `拨号这类永不拦截的包名放行`() {
        val decision = Enforcer.decide(
            GuardInput(
                foregroundPackage = douyin.packageName,
                allowances = mapOf(douyin.packageName to douyin.copy(usedMs = 99_000_000L)),
                nowElapsedMs = 1_000L,
                sessionStartElapsedMs = null,
                neverBlockPackages = setOf(douyin.packageName)
            )
        )
        assertTrue(decision is Decision.Allow)
    }

    @Test
    fun `已用时长等于库里累计加本次会话`() {
        val now = 10_000_000L
        val i = input(usedMs = 60_000L, sessionStart = now - 30_000L, now = now)
        assertEquals(90_000L, Enforcer.usedMs(i, douyin.copy(usedMs = 60_000L)))
    }
}
