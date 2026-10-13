package kr.co.gcflarchive.student.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDateTime
import kr.co.gcflarchive.core.map.Place
import kr.co.gcflarchive.core.net.UploadFile

/*
 * The few things that differ between iPhone/iPad, Android and desktop. Everything
 * else in the student app is shared Compose code.
 */

/** Share sheet with plain text (links, codes). */
expect fun shareText(text: String)

/**
 * Hands downloaded bytes to the user: Android saves to 다운로드, iOS opens the share
 * sheet ("파일에 저장", AirDrop, 다른 앱에서 열기), desktop saves to ~/Downloads.
 */
expect suspend fun saveFile(name: String, bytes: ByteArray, mimeType: String?)

/** Returns a launcher that opens the system document picker (multiple files). */
@Composable
expect fun rememberFilePicker(onPicked: (List<UploadFile>) -> Unit): () -> Unit

/** System back (Android back button / gesture); no-op where the platform has none. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Map with one marker per place: Apple 지도 on iOS, OpenStreetMap on Android. */
@Composable
expect fun PlaceMap(places: List<Place>, selected: Place?, onSelect: (Place) -> Unit, modifier: Modifier)

/** A local notification to deliver at [at] (device time zone). */
data class ScheduledNotice(val id: String, val at: LocalDateTime, val title: String, val body: String)

/** Local notifications for 급식 알림 (no server push needed). */
expect object Notifications {
    /** Asks for permission if needed; returns whether notifications may be shown. */
    suspend fun requestPermission(): Boolean
    /** Replaces every pending meal notification with [items]. */
    fun replaceMealNotices(items: List<ScheduledNotice>)
    fun cancelMealNotices()
}

/** "iOS 18.1" / "Android 15" — shown in 설정. */
expect val platformName: String
