@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package kr.co.gcflarchive.student.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.core.map.Place
import kr.co.gcflarchive.core.net.UploadFile
import platform.CoreGraphics.CGRectMake
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSData
import platform.Foundation.NSDateComponents
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private fun topController(): UIViewController? {
    var c = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (c?.presentedViewController != null) c = c.presentedViewController
    return c
}

/** Share sheets need an anchor on iPad or they crash. */
private fun present(vc: UIActivityViewController) {
    val top = topController() ?: return
    vc.popoverPresentationController?.let { pop ->
        pop.sourceView = top.view
        top.view.bounds.useContents { pop.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
        pop.permittedArrowDirections = 0uL
    }
    top.presentViewController(vc, animated = true, completion = null)
}

actual fun shareText(text: String) {
    present(UIActivityViewController(activityItems = listOf(text), applicationActivities = null))
}

actual suspend fun saveFile(name: String, bytes: ByteArray, mimeType: String?): Unit = withContext(Dispatchers.Main) {
    val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "file" }
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + safe)
    bytes.toNSData().writeToURL(url, atomically = true)
    present(UIActivityViewController(activityItems = listOf(url), applicationActivities = null))
}

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }

private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

private class PickerDelegate(var onPicked: (List<UploadFile>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val files = didPickDocumentsAtURLs.mapNotNull { it as? NSURL }.mapNotNull { url ->
            val scoped = url.startAccessingSecurityScopedResource()
            val data = NSData.dataWithContentsOfURL(url)
            if (scoped) url.stopAccessingSecurityScopedResource()
            data?.let { UploadFile(url.lastPathComponent ?: "file", it.toByteArray(), null) }
        }
        if (files.isNotEmpty()) onPicked(files)
    }
}

@Composable
actual fun rememberFilePicker(onPicked: (List<UploadFile>) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onPicked)
    // The picker keeps only a weak reference to its delegate; remember holds it.
    val delegate = remember { PickerDelegate { latest.value(it) } }
    return remember {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = true)
            picker.allowsMultipleSelection = true
            picker.delegate = delegate
            topController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** iOS has no system back button; screens show ← in their top bar. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

private class MapDelegate(var places: Map<MKPointAnnotation, Place>, var onSelect: (Place) -> Unit) : NSObject(), MKMapViewDelegateProtocol {
    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val annotation = didSelectAnnotationView.annotation as? MKPointAnnotation ?: return
        places[annotation]?.let(onSelect)
    }
}

@Composable
actual fun PlaceMap(places: List<Place>, selected: Place?, onSelect: (Place) -> Unit, modifier: Modifier) {
    val delegate = remember { MapDelegate(emptyMap(), onSelect) }
    delegate.onSelect = onSelect
    UIKitView(
        factory = {
            MKMapView().apply {
                this.delegate = delegate
                setRegion(MKCoordinateRegionMakeWithDistance(CLLocationCoordinate2DMake(37.4313541, 126.9892723), 1500.0, 1500.0), animated = false)
            }
        },
        modifier = modifier,
        update = { map ->
            map.removeAnnotations(map.annotations)
            val pins = places.associateBy { p ->
                MKPointAnnotation().apply {
                    setCoordinate(CLLocationCoordinate2DMake(p.y, p.x))
                    setTitle(p.name)
                }
            }
            delegate.places = pins
            map.addAnnotations(pins.keys.toList())
            val focus = selected?.let { s -> pins.entries.firstOrNull { it.value.id == s.id } }
            when {
                focus != null -> {
                    val place = focus.value
                    map.setRegion(MKCoordinateRegionMakeWithDistance(CLLocationCoordinate2DMake(place.y, place.x), 600.0, 600.0), animated = true)
                    map.selectAnnotation(focus.key as MKAnnotationProtocol, animated = true)
                }
                pins.isNotEmpty() -> map.showAnnotations(pins.keys.toList(), animated = true)
            }
        },
    )
    DisposableEffect(Unit) { onDispose { delegate.places = emptyMap() } }
}

actual object Notifications {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    private const val PREFIX = "meal-"

    actual suspend fun requestPermission(): Boolean = suspendCoroutine { cont ->
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            cont.resume(granted)
        }
    }

    actual fun replaceMealNotices(items: List<ScheduledNotice>) {
        center.getPendingNotificationRequestsWithCompletionHandler { pending ->
            val old = pending.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }.filter { it.startsWith(PREFIX) }
            center.removePendingNotificationRequestsWithIdentifiers(old)
            items.forEach { n ->
                val content = UNMutableNotificationContent().apply {
                    setTitle(n.title)
                    setBody(n.body)
                    setSound(UNNotificationSound.defaultSound())
                }
                val when_ = NSDateComponents().apply {
                    year = n.at.year.toLong()
                    month = n.at.monthNumber.toLong()
                    day = n.at.dayOfMonth.toLong()
                    hour = n.at.hour.toLong()
                    minute = n.at.minute.toLong()
                }
                val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(when_, repeats = false)
                center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(PREFIX + n.id, content, trigger), withCompletionHandler = null)
            }
        }
    }

    actual fun cancelMealNotices() {
        center.getPendingNotificationRequestsWithCompletionHandler { pending ->
            center.removePendingNotificationRequestsWithIdentifiers(
                pending.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }.filter { it.startsWith(PREFIX) },
            )
        }
    }
}

actual val platformName: String get() = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"

/** Face ID / Touch ID, falling back to the device passcode (LAPolicyDeviceOwnerAuthentication). */
actual suspend fun authenticateUser(reason: String): Boolean = suspendCoroutine { cont ->
    val context = LAContext()
    if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)) {
        cont.resume(false)
        return@suspendCoroutine
    }
    context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { success, _ -> cont.resume(success) }
}
