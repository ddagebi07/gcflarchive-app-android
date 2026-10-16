package kr.co.gcflarchive.student.platform

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.core.map.Place
import kr.co.gcflarchive.core.net.UploadFile
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Set by the Android host activity before showing the shared UI. */
@SuppressLint("StaticFieldLeak")
object AndroidPlatform {
    lateinit var context: Context
}

actual fun shareText(text: String) {
    val ctx = AndroidPlatform.context
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

actual suspend fun saveFile(name: String, bytes: ByteArray, mimeType: String?): Unit = withContext(Dispatchers.IO) {
    val ctx = AndroidPlatform.context
    val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "file" }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safe)
            mimeType?.let { put(MediaStore.Downloads.MIME_TYPE, it) }
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(dir, safe).writeBytes(bytes)
    }
}

@Composable
actual fun rememberFilePicker(onPicked: (List<UploadFile>) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val files = uris.mapNotNull { uri ->
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
            var name = uri.lastPathSegment ?: "file"
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
            UploadFile(name, bytes, ctx.contentResolver.getType(uri))
        }
        if (files.isNotEmpty()) onPicked(files)
    }
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
actual fun rememberImagePicker(max: Int, onPicked: (List<UploadFile>) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val latest = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    // PickMultipleVisualMedia needs a limit of at least 2.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(max.coerceAtLeast(2))) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.take(max).mapIndexedNotNull { i, uri -> runCatching { uploadablePhoto(ctx, uri, i + 1) }.getOrNull() }
            }
            if (files.isNotEmpty()) latest.value(files)
        }
    }
    return { if (max > 0) launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

private const val PHOTO_PASS_THROUGH_BYTES = 15 * 1024 * 1024
private const val PHOTO_MAX_SIDE = 1600

/** JPG/PNG/GIF/WEBP go as they are (the server strips metadata); others (HEIC) become JPEG here. */
private fun uploadablePhoto(ctx: Context, uri: Uri, n: Int): UploadFile? {
    val type = ctx.contentResolver.getType(uri).orEmpty()
    val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (type in setOf("image/jpeg", "image/png", "image/gif", "image/webp") && raw.size <= PHOTO_PASS_THROUGH_BYTES) {
        return UploadFile("photo$n.${type.substringAfter('/').replace("jpeg", "jpg")}", raw, type)
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return UploadFile("photo$n", raw, type.ifBlank { null })
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(raw))) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val scale = minOf(1f, PHOTO_MAX_SIDE.toFloat() / maxOf(w, h))
        decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val out = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    bitmap.recycle()
    return UploadFile("photo$n.jpg", out.toByteArray(), "image/jpeg")
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun PlaceMap(pins: List<MapPin>, selected: Place?, onSelect: (Place) -> Unit, modifier: Modifier, onInteract: () -> Unit) {
    // Not used by the Android app yet (it has its own colored pins in MapActivity): plain markers.
    val places = pins.map { it.place }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().apply {
                userAgentValue = ctx.packageName
                osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
                osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
            }
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.5)
                controller.setCenter(GeoPoint(37.4313541, 126.9892723))
                overlays.add(CopyrightOverlay(ctx))
                // A touch on the map folds the list away; a marker tap (on finger up) opens it again.
                setOnTouchListener { _, e -> if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) onInteract(); false }
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker }
            places.forEach { p ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(p.y, p.x)
                    title = p.name
                    setOnMarkerClickListener { _, _ -> onSelect(p); true }
                })
            }
            when {
                selected != null -> map.controller.animateTo(GeoPoint(selected.y, selected.x), 17.5, 400L)
                places.size > 1 -> map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(places.map { GeoPoint(it.y, it.x) }).increaseByScale(1.3f), true) }
                places.size == 1 -> map.controller.animateTo(GeoPoint(places[0].y, places[0].x), 17.5, 400L)
            }
            map.invalidate()
        },
    )
}

/** The current Android app schedules meal notifications with WorkManager (app/meal). */
actual object Notifications {
    actual suspend fun requestPermission(): Boolean = true
    actual fun replaceMealNotices(items: List<ScheduledNotice>) = Unit
    actual fun cancelMealNotices() = Unit
    // The Android app posts 공지 알림 from its own WorkManager job (app/notice/NoticeNotifier).
    actual fun showNow(id: String, title: String, body: String) = Unit
}

actual fun applySystemTheme(mode: String) {}

actual val deviceLanguage: String get() = java.util.Locale.getDefault().language

actual val platformName: String get() = "Android ${Build.VERSION.RELEASE}"

/**
 * Not wired yet: the Android app shows this code through its own BiometricPrompt
 * (app/util/Biometric.kt). Refusing keeps the code hidden if this host is ever used.
 */
actual suspend fun authenticateUser(reason: String): Boolean = false

// The Android student app is the View-based :app module; this target only keeps the shared UI building.
actual fun canAuthenticateUser(): Boolean = false

@Composable
actual fun AppForegroundEffect(onForeground: () -> Unit, onBackground: () -> Unit) = Unit
