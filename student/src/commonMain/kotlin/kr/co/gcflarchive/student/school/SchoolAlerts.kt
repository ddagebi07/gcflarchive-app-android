package kr.co.gcflarchive.student.school

import kr.co.gcflarchive.core.Services
import kr.co.gcflarchive.student.platform.Notifications
import kr.co.gcflarchive.student.platform.platformName

/**
 * 학교생활 알림 (iPhone/iPad): registers this device with the server and, when the device has no
 * FCM token yet, shows new alerts from the inbox. Runs with NoticeAlerts (app open / back to front /
 * iOS background refresh). The Android app has its own FCM service and WorkManager job.
 */
object SchoolAlerts {
    suspend fun check(services: Services): Int {
        if (runCatching { services.school.me() }.getOrNull()?.loggedIn != true) return 0
        val platform = if (platformName.startsWith("Android")) "android" else "ios"
        runCatching { services.school.registerThisDevice(services.prefs, platform) }
        val alerts = runCatching { services.school.newAlerts(services.prefs) }.getOrDefault(emptyList())
        alerts.forEach { Notifications.showNow("school-${it.id}", it.title, it.body) }
        return alerts.size
    }

    /** iOS: Firebase handed us a (new) FCM token. */
    suspend fun onPushToken(services: Services, token: String) {
        services.prefs.pushToken = token
        runCatching { services.prefs.serverPushes = services.school.registerDevice(token, "ios") }
    }
}
