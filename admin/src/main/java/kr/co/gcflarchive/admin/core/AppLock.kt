package kr.co.gcflarchive.admin.core

/**
 * Re-lock policy: biometric/device-credential unlock is required when the app comes
 * back after more than [backgroundGraceMs] in the background (short grace so the file
 * picker / share sheet doesn't trigger it), or after [idleMs] without interaction.
 */
class AppLock(
    private val backgroundGraceMs: Long = 30_000,
    private val idleMs: Long = 5 * 60_000,
) {
    @Volatile var locked: Boolean = true
        private set
    private var backgroundedAt: Long = 0
    private var lastInteraction: Long = 0

    fun onUnlocked(now: Long) {
        locked = false
        lastInteraction = now
    }

    fun onInteraction(now: Long) {
        lastInteraction = now
    }

    fun onBackground(now: Long) {
        backgroundedAt = now
    }

    fun onForeground(now: Long) {
        if (backgroundedAt > 0 && now - backgroundedAt > backgroundGraceMs) locked = true
        backgroundedAt = 0
    }

    /** Checked periodically while in the foreground. */
    fun checkIdle(now: Long): Boolean {
        if (!locked && lastInteraction > 0 && now - lastInteraction > idleMs) locked = true
        return locked
    }

    fun lockNow() {
        locked = true
    }
}
