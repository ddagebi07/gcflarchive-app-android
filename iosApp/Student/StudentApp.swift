import SwiftUI
import UserNotifications
import BackgroundTasks
import StudentKit
#if canImport(FirebaseMessaging)
import FirebaseCore
import FirebaseMessaging
#endif

/// iPhone / iPad entry point. Everything on screen is the shared Compose UI
/// (`StudentApp` in the Kotlin :student module); this file only hosts it.
@main
struct StudentIOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                // gcflarchive://open?url=… or a site link shared to the app.
                .onOpenURL { url in
                    MainViewControllerKt.openIncomingLink(url: AppLinks.siteURL(from: url))
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        MainViewControllerKt.openIncomingLink(url: url.absoluteString)
                    }
                }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(startLink: nil)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

enum AppLinks {
    /// gcflarchive://notice-view?id=3 → https://gcflarchive.co.kr/notice-view?id=3
    static func siteURL(from url: URL) -> String {
        guard url.scheme == "gcflarchive" else { return url.absoluteString }
        var path = (url.host ?? "") + url.path
        if let q = url.query { path += "?" + q }
        return "https://gcflarchive.co.kr/" + path
    }
}

/// Shows 급식·공지 알림 as a banner even while the app is open, and checks for 새 공지
/// in the background (iOS decides how often; usually every few hours at most).
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    static let noticeTaskID = "kr.co.gcflarchive.student.notice-refresh"

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.noticeTaskID, using: nil) { task in
            self.handleNoticeRefresh(task as! BGAppRefreshTask)
        }
        NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { _ in
            Self.scheduleNoticeRefresh()
        }
        // 학교생활 푸시: only when Firebase is linked and its config file is in the app (PUSH_SETUP.md).
        // Without it the app checks the 학교생활 inbox when it opens and in background refresh.
        #if canImport(FirebaseMessaging)
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
            application.registerForRemoteNotifications()
        }
        #endif
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        #if canImport(FirebaseMessaging)
        Messaging.messaging().apnsToken = deviceToken
        #endif
    }

    static func scheduleNoticeRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: noticeTaskID)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    private func handleNoticeRefresh(_ task: BGAppRefreshTask) {
        Self.scheduleNoticeRefresh() // keep the chain going
        task.expirationHandler = { task.setTaskCompleted(success: false) }
        MainViewControllerKt.checkNoticePushes { ok in
            task.setTaskCompleted(success: ok.boolValue)
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let id = response.notification.request.identifier
        if let target = response.notification.request.content.userInfo["target"] as? String {
            MainViewControllerKt.openPushTarget(target: target)   // FCM: e.g. "visit:12"
        } else if id.hasPrefix("school-") {
            MainViewControllerKt.openIncomingLink(url: "https://gcflarchive.co.kr/school?tab=alerts")
        } else if id.hasPrefix("meal-") {
            MainViewControllerKt.openIncomingLink(url: "https://gcflarchive.co.kr/meal")
        } else if id.hasPrefix("notice-") {
            MainViewControllerKt.openIncomingLink(url: "https://gcflarchive.co.kr/notice-view?id=" + id.dropFirst("notice-".count))
        }
        completionHandler()
    }
}

#if canImport(FirebaseMessaging)
/// FCM: hands the FCM token to the server (학교생활 푸시).
extension AppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        if let token = fcmToken { MainViewControllerKt.registerPushToken(token: token) }
    }
}
#endif
