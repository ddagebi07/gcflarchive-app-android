package kr.co.gcflarchive.student.notice

import kr.co.gcflarchive.core.Services
import kr.co.gcflarchive.student.platform.Notifications

/**
 * 새 공지 알림 (iPhone/iPad). Runs when the app opens or comes back, and from iOS background
 * refresh (iosApp AppDelegate → checkNoticePushes). The Android app has its own WorkManager job.
 */
object NoticeAlerts {
    /** Returns how many notifications were shown; network errors just mean "try next time". */
    suspend fun check(services: Services): Int {
        val pushes = runCatching { services.notices.newPushes(services.prefs) }.getOrDefault(emptyList())
        pushes.forEach { p ->
            Notifications.showNow("notice-${p.id}", if (p.critical) "중요 공지가 올라왔어요" else "새 공지가 올라왔어요", p.title)
        }
        return pushes.size
    }
}
