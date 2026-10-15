package kr.co.gcflarchive.student

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.launch
import kr.co.gcflarchive.student.notice.NoticeAlerts
import platform.UIKit.UIViewController

/**
 * Entry point for the Xcode app (iosApp/Student/StudentApp.swift):
 * `MainViewControllerKt.MainViewController(startLink: nil)`.
 */
fun MainViewController(startLink: String?): UIViewController = ComposeUIViewController { StudentApp(startLink = startLink) }

/** Called from Swift's onOpenURL / onContinueUserActivity. */
fun openIncomingLink(url: String) = IncomingLinks.open(url)

/**
 * iOS background refresh (BGAppRefreshTask in AppDelegate): checks for 새 공지 and shows
 * notifications; [completion] gets whether the check finished (for setTaskCompleted).
 */
fun checkNoticePushes(completion: (Boolean) -> Unit) {
    kotlinx.coroutines.MainScope().launch {
        val ok = runCatching {
            NoticeAlerts.check(sharedServices)
            kr.co.gcflarchive.student.school.SchoolAlerts.check(sharedServices)
        }.isSuccess
        completion(ok)
    }
}

/** Called from Swift when Firebase Messaging hands over the device's FCM token (PUSH_SETUP.md). */
fun registerPushToken(token: String) {
    kotlinx.coroutines.MainScope().launch { kr.co.gcflarchive.student.school.SchoolAlerts.onPushToken(sharedServices, token) }
}

/** Called from Swift when the user taps a push: opens what it points at (e.g. "visit:12"). */
fun openPushTarget(target: String) = IncomingLinks.open("/school?target=" + target)
