import SwiftUI
import UserNotifications
import StudentKit

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

/// Shows 급식 알림 as a banner even while the app is open.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if response.notification.request.identifier.hasPrefix("meal-") {
            MainViewControllerKt.openIncomingLink(url: "https://gcflarchive.co.kr/meal")
        }
        completionHandler()
    }
}
