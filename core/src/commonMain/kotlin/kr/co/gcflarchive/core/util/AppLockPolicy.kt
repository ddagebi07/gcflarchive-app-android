package kr.co.gcflarchive.core.util

/**
 * 앱 잠금 시점: 앱을 새로 열 때, 그리고 [graceMs]보다 오래 백그라운드에 있다가 돌아올 때.
 * 짧은 유예가 있어서 파일 선택·공유 창·인증 창을 다녀와도 다시 잠기지 않아요.
 * (Android 앱의 util/AppLockPolicy와 같은 규칙.)
 */
class AppLockPolicy(private val graceMs: Long = 60_000) {
    var locked: Boolean = true
        private set
    private var backgroundedAt: Long = 0

    fun onUnlocked() {
        locked = false
    }

    fun onBackground(now: Long) {
        backgroundedAt = now
    }

    fun onForeground(now: Long) {
        if (backgroundedAt > 0 && now - backgroundedAt > graceMs) locked = true
        backgroundedAt = 0
    }

    fun needsUnlock(enabled: Boolean): Boolean = enabled && locked
}
