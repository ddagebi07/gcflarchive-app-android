package kr.co.gcflarchive.student.platform

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
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
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun PlaceMap(places: List<Place>, selected: Place?, onSelect: (Place) -> Unit, modifier: Modifier) {
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
}

actual val platformName: String get() = "Android ${Build.VERSION.RELEASE}"

/**
 * Not wired yet: the Android app shows this code through its own BiometricPrompt
 * (app/util/Biometric.kt). Refusing keeps the code hidden if this host is ever used.
 */
actual suspend fun authenticateUser(reason: String): Boolean = false
