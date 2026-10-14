package kr.co.gcflarchive.core.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockPolicyTest {
    @Test
    fun locksOnLaunchAndAfterLongBackground() {
        val p = AppLockPolicy(graceMs = 60_000)
        assertFalse(p.needsUnlock(enabled = false))
        assertTrue(p.needsUnlock(enabled = true))
        p.onUnlocked()
        p.onBackground(1_000)
        p.onForeground(30_000)
        assertFalse(p.needsUnlock(enabled = true))
        p.onBackground(100_000)
        p.onForeground(200_000)
        assertTrue(p.needsUnlock(enabled = true))
    }
}
