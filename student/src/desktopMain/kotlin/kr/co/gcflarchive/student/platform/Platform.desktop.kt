package kr.co.gcflarchive.student.platform

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.core.map.Place
import kr.co.gcflarchive.core.net.UploadFile
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/* Desktop is only a preview target for developers (./gradlew :student:run). */

actual fun shareText(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

actual suspend fun saveFile(name: String, bytes: ByteArray, mimeType: String?) = withContext(Dispatchers.IO) {
    File(System.getProperty("user.home"), "Downloads/$name").writeBytes(bytes)
}

@Composable
actual fun rememberFilePicker(onPicked: (List<UploadFile>) -> Unit): () -> Unit = {
    val dialog = FileDialog(null as Frame?, "파일 선택", FileDialog.LOAD).apply { isMultipleMode = true; isVisible = true }
    val files = dialog.files.orEmpty().map { UploadFile(it.name, it.readBytes(), null) }
    if (files.isNotEmpty()) onPicked(files)
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun PlaceMap(places: List<Place>, selected: Place?, onSelect: (Place) -> Unit, modifier: Modifier) {
    Column(modifier) { Text("지도는 휴대폰 앱에서 표시됩니다. (${places.size}곳)") }
}

actual object Notifications {
    actual suspend fun requestPermission(): Boolean = false
    actual fun replaceMealNotices(items: List<ScheduledNotice>) = Unit
    actual fun cancelMealNotices() = Unit
}

actual val platformName: String get() = "Desktop ${System.getProperty("os.name")}"
