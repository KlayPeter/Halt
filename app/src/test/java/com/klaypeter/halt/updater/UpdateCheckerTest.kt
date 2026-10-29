package com.klaypeter.halt.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `线上版本更大`() {
        assertTrue(UpdateChecker.isNewer("v0.1.3", "0.1.2"))
        assertTrue(UpdateChecker.isNewer("0.2.0", "0.1.9"))
        assertTrue(UpdateChecker.isNewer("v1.0.0", "0.9.9"))
    }

    @Test
    fun `同版本不算更新`() {
        assertFalse(UpdateChecker.isNewer("v0.1.2", "0.1.2"))
        assertFalse(UpdateChecker.isNewer("0.1.2", "v0.1.2"))
    }

    @Test
    fun `线上版本更小`() {
        assertFalse(UpdateChecker.isNewer("v0.1.1", "0.1.2"))
    }

    @Test
    fun `位数不同也能比`() {
        assertTrue(UpdateChecker.isNewer("0.2", "0.1.9"))
        assertFalse(UpdateChecker.isNewer("0.1", "0.1.0"))
    }

    @Test
    fun `带后缀不会崩`() {
        assertTrue(UpdateChecker.isNewer("v0.2.0-beta", "0.1.9"))
    }
}