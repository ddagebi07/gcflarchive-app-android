package kr.co.gcflarchive.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test
    fun locksOnLaunchOnlyWhenTurnedOn() {
        val p = AppLockPolicy()
        assertFalse(p.needsUnlock(enabled = false))
        assertTrue(p.needsUnlock(enabled = true))
        p.onUnlocked()
        assertFalse(p.needsUnlock(enabled = true))
    }

    @Test
    fun shortTripsDoNotRelock() {
        val p = AppLockPolicy(graceMs = 60_000)
        p.onUnlocked()
        p.onBackground(1_000)
        p.onForeground(30_000) // file picker, share sheet, the biometric screen itself
        assertFalse(p.needsUnlock(enabled = true))
        p.onBackground(100_000)
        p.onForeground(200_000)
        assertTrue(p.needsUnlock(enabled = true))
    }
}
