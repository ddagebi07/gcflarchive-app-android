package kr.co.gcflarchive.app.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kr.co.gcflarchive.app.LockActivity
import kr.co.gcflarchive.app.data.AppPrefs

/** 설정의 '앱을 열 때 생체 인증'이 켜져 있으면, 앱이 앞으로 나올 때 잠금 화면을 먼저 띄워요. */
object AppLock : Application.ActivityLifecycleCallbacks {
    val policy = AppLockPolicy()
    private var started = 0
    private lateinit var prefs: AppPrefs

    fun install(app: Application) {
        prefs = AppPrefs(app)
        app.registerActivityLifecycleCallbacks(this)
    }

    /** After turning the lock on in 설정, the current session stays open. */
    fun markUnlocked() = policy.onUnlocked()

    override fun onActivityStarted(activity: Activity) {
        if (started++ == 0) policy.onForeground(System.currentTimeMillis())
    }

    override fun onActivityStopped(activity: Activity) {
        if (--started == 0) policy.onBackground(System.currentTimeMillis())
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is LockActivity) return
        if (policy.needsUnlock(prefs.appLockEnabled)) {
            activity.startActivity(LockActivity.intent(activity))
            activity.overridePendingTransition(0, 0)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
