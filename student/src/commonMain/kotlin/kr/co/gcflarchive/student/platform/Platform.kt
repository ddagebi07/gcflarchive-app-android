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

/**
 * Returns a launcher for the photo picker (up to [max] photos). Photos come back ready to
 * upload: formats the server can't read (HEIC) are turned into JPEG on the device.
 */
@Composable
expect fun rememberImagePicker(max: Int, onPicked: (List<UploadFile>) -> Unit): () -> Unit

/** System back (Android back button / gesture); no-op where the platform has none. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * One marker on [PlaceMap]: [colorArgb] (0xAARRGGBB) tints it, e.g. by 업종 for 극플인의 추천,
 * and [label] is drawn inside (the 후기 count). Both null = the platform's default pin.
 */
data class MapPin(val place: Place, val colorArgb: Long? = null, val label: String? = null)

/** Map with one marker per pin: Apple 지도 on iOS, OpenStreetMap on Android. */
@Composable
expect fun PlaceMap(pins: List<MapPin>, selected: Place?, onSelect: (Place) -> Unit, modifier: Modifier)

/** A local notification to deliver at [at] (device time zone). */
data class ScheduledNotice(val id: String, val at: LocalDateTime, val title: String, val body: String)

/** Local notifications for 급식 알림 (no server push needed). */
expect object Notifications {
    /** Asks for permission if needed; returns whether notifications may be shown. */
    suspend fun requestPermission(): Boolean
    /** Replaces every pending meal notification with [items]. */
    fun replaceMealNotices(items: List<ScheduledNotice>)
    fun cancelMealNotices()
    /** Shows a notification right away (새 공지). [id] keeps repeats from stacking. */
    fun showNow(id: String, title: String, body: String)
}

/**
 * 설정 > 화면 모드 for the system parts the app doesn't draw (iPhone·iPad: status bar, keyboard, share sheet):
 * "light" / "dark" / "system". Android: the app sets AppCompat's night mode itself (GcflApp).
 */
expect fun applySystemTheme(mode: String)

/** The device's language ("ko", "en", …): 첫 실행 때 한국어가 아니면 한국어/English를 물어봐요. */
expect val deviceLanguage: String

/** "iOS 18.1" / "Android 15" — shown in 설정. */
expect val platformName: String

/**
 * Asks the device owner to confirm with 지문 / Face ID (or the device passcode when
 * no biometrics are enrolled). Returns whether it succeeded. Used before showing the
 * saved 등급컷 익명 접근 코드.
 */
expect suspend fun authenticateUser(reason: String): Boolean

/** Whether Face ID / Touch ID / 지문 or a device passcode is set up (needed for 앱 잠금). */
expect fun canAuthenticateUser(): Boolean

/** Calls [onForeground] / [onBackground] when the app comes back or leaves (앱 잠금 timing). */
@Composable
expect fun AppForegroundEffect(onForeground: () -> Unit, onBackground: () -> Unit)
