@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package kr.co.gcflarchive.student.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
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
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKPointAnnotation
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
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
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

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

/** 사진 보관함에서 고른 사진을 JPEG로 바꿔서 넘겨줘요 (HEIC도 서버가 읽을 수 있게). */
private class PhotoPickerDelegate(var onPicked: (List<UploadFile>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.mapNotNull { it as? PHPickerResult }
        if (results.isEmpty()) return
        val files = arrayOfNulls<UploadFile>(results.size)
        var pending = results.size
        results.forEachIndexed { i, result ->
            result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
                val jpeg = data?.let { UIImage(data = it) }?.let { UIImageJPEGRepresentation(it, 0.85) }
                dispatch_async(dispatch_get_main_queue()) {
                    if (jpeg != null) files[i] = UploadFile("photo${i + 1}.jpg", jpeg.toByteArray(), "image/jpeg")
                    pending -= 1
                    if (pending == 0) files.filterNotNull().takeIf { it.isNotEmpty() }?.let { onPicked(it) }
                }
            }
        }
    }
}

@Composable
actual fun rememberImagePicker(max: Int, onPicked: (List<UploadFile>) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onPicked)
    val delegate = remember { PhotoPickerDelegate { latest.value(it) } }
    return remember(max) {
        {
            if (max > 0) {
                val config = PHPickerConfiguration()
                config.selectionLimit = max.toLong()
                config.filter = PHPickerFilter.imagesFilter
                val picker = PHPickerViewController(configuration = config)
                picker.delegate = delegate
                topController()?.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

/** iOS has no system back button; screens show ← in their top bar. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

private const val PIN_ID = "pin"

private class MapDelegate(var pins: Map<MKPointAnnotation, MapPin>, var onSelect: (Place) -> Unit) : NSObject(), MKMapViewDelegateProtocol {
    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val annotation = didSelectAnnotationView.annotation as? MKPointAnnotation ?: return
        pins[annotation]?.let { onSelect(it.place) }
    }

    /** Balloon markers tinted by 업종 with the 후기 count inside (극플인의 추천). */
    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        val annotation = viewForAnnotation as? MKPointAnnotation ?: return null
        val pin = pins[annotation] ?: return null
        val view = mapView.dequeueReusableAnnotationViewWithIdentifier(PIN_ID) as? MKMarkerAnnotationView
            ?: MKMarkerAnnotationView(annotation = annotation, reuseIdentifier = PIN_ID)
        view.annotation = annotation
        view.markerTintColor = pin.colorArgb?.let(::uiColor)
        view.glyphText = pin.label
        return view
    }
}

private fun uiColor(argb: Long): UIColor = UIColor.colorWithRed(
    red = ((argb shr 16) and 0xFF) / 255.0,
    green = ((argb shr 8) and 0xFF) / 255.0,
    blue = (argb and 0xFF) / 255.0,
    alpha = ((argb shr 24) and 0xFF) / 255.0,
)

@Composable
actual fun PlaceMap(pins: List<MapPin>, selected: Place?, onSelect: (Place) -> Unit, modifier: Modifier) {
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
            val byAnnotation = pins.associateBy { pin ->
                MKPointAnnotation().apply {
                    setCoordinate(CLLocationCoordinate2DMake(pin.place.y, pin.place.x))
                    setTitle(pin.place.name)
                }
            }
            delegate.pins = byAnnotation
            map.addAnnotations(byAnnotation.keys.toList())
            val focus = selected?.let { s -> byAnnotation.entries.firstOrNull { it.value.place.id == s.id } }
            when {
                focus != null -> {
                    val place = focus.value.place
                    map.setRegion(MKCoordinateRegionMakeWithDistance(CLLocationCoordinate2DMake(place.y, place.x), 600.0, 600.0), animated = true)
                    map.selectAnnotation(focus.key as MKAnnotationProtocol, animated = true)
                }
                byAnnotation.isNotEmpty() -> map.showAnnotations(byAnnotation.keys.toList(), animated = true)
            }
        },
    )
    DisposableEffect(Unit) { onDispose { delegate.pins = emptyMap() } }
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

    /** Delivered at once (no trigger); AppDelegate shows it as a banner even in the app. */
    actual fun showNow(id: String, title: String, body: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
        }
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, content, null), withCompletionHandler = null)
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

actual fun canAuthenticateUser(): Boolean = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

@Composable
actual fun AppForegroundEffect(onForeground: () -> Unit, onBackground: () -> Unit) {
    val foreground by rememberUpdatedState(onForeground)
    val background by rememberUpdatedState(onBackground)
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        val enter = center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, queue) { _ -> foreground() }
        val leave = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { _ -> background() }
        onDispose {
            center.removeObserver(enter)
            center.removeObserver(leave)
        }
    }
}
