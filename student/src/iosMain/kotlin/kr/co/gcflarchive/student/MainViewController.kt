package kr.co.gcflarchive.student

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point for the Xcode app (iosApp/Student/StudentApp.swift):
 * `MainViewControllerKt.MainViewController(startLink: nil)`.
 */
fun MainViewController(startLink: String?): UIViewController = ComposeUIViewController { StudentApp(startLink = startLink) }

/** Called from Swift's onOpenURL / onContinueUserActivity. */
fun openIncomingLink(url: String) = IncomingLinks.open(url)
