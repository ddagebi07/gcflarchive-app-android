package kr.co.gcflarchive.app.share

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.data.Http
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException

data class PickedFile(
    val uri: Uri,
    val name: String,
    val size: Long,         // -1 when the provider doesn't report it
    val mimeType: String?,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val isAllowed: Boolean get() = extension in Config.DRIVE_ALLOWED_EXTENSIONS
}

data class UploadedFile(val name: String, val shortCode: String) {
    val shortUrl: String get() = Config.url("/s/$shortCode")
}

sealed interface UploadResult {
    data class Success(val files: List<UploadedFile>, val zipped: Boolean) : UploadResult
    data object NeedLogin : UploadResult
    data class Failure(val message: String) : UploadResult
}

/**
 * Native client for POST /api/share/upload (routes/temp_share.py). Authenticates with
 * the same Flask session cookie as every other screen, so logging in once in the
 * app is enough for share-sheet uploads.
 */
class DriveUploader(private val resolver: ContentResolver) {

    fun describe(uri: Uri): PickedFile {
        var name: String? = null
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        }
        val mime = resolver.getType(uri)
        var fileName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        // Some apps share files without an extension in the display name; recover it from the MIME type.
        if (!fileName.contains('.')) {
            mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }?.let { fileName = "$fileName.$it" }
        }
        return PickedFile(uri, fileName, size, mime)
    }

    suspend fun upload(files: List<PickedFile>, onProgress: (Float) -> Unit): UploadResult = withContext(Dispatchers.IO) {
        val cookie = CookieManager.getInstance().getCookie(Config.BASE_URL)
        if (cookie.isNullOrBlank()) return@withContext UploadResult.NeedLogin

        val total = files.sumOf { it.size.coerceAtLeast(0) }.coerceAtLeast(1)
        var sent = 0L
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            for (f in files) {
                addFormDataPart("files", f.name, UriRequestBody(f, f.mimeType?.toMediaTypeOrNull()) { n ->
                    sent += n
                    onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
                })
            }
        }.build()

        val request = Request.Builder()
            .url(Config.url("/api/share/upload"))
            .header("Cookie", cookie)
            .header("User-Agent", "${System.getProperty("http.agent").orEmpty()} ${Config.USER_AGENT_SUFFIX}".trim())
            .post(body)
            .build()

        try {
            Http.client.newCall(request).execute().use { res ->
                // Flask re-issues the session cookie; keep the shared cookie store in sync.
                res.headers("Set-Cookie").forEach { CookieManager.getInstance().setCookie(Config.BASE_URL, it) }
                CookieManager.getInstance().flush()

                val text = res.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (res.code == 401) return@use UploadResult.NeedLogin
                if (json == null) {
                    // An expired session can get bounced to the HTML verify page rather than a JSON 401.
                    return@use if (res.request.url.encodedPath.startsWith("/verify")) {
                        UploadResult.NeedLogin
                    } else {
                        UploadResult.Failure("서버 응답을 해석할 수 없습니다. (HTTP ${res.code})")
                    }
                }
                when {
                    !res.isSuccessful -> UploadResult.Failure(json.optString("error").ifBlank { "업로드 실패 (HTTP ${res.code})" })
                    else -> {
                        val arr = json.optJSONArray("files")
                        val uploaded = (0 until (arr?.length() ?: 0)).map { i ->
                            val o = arr!!.getJSONObject(i)
                            UploadedFile(o.optString("filename"), o.optString("short_code"))
                        }
                        UploadResult.Success(uploaded, json.optBoolean("zipped"))
                    }
                }
            }
        } catch (e: IOException) {
            UploadResult.Failure("네트워크 오류: ${e.message ?: "연결할 수 없습니다."}")
        }
    }

    private inner class UriRequestBody(
        private val file: PickedFile,
        private val type: MediaType?,
        private val onBytes: (Long) -> Unit,
    ) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = file.size
        override fun writeTo(sink: BufferedSink) {
            val input = resolver.openInputStream(file.uri) ?: throw IOException("${file.name}을(를) 열 수 없습니다.")
            input.source().use { source ->
                while (true) {
                    val n = source.read(sink.buffer, 64 * 1024L)
                    if (n == -1L) break
                    sink.flush()
                    onBytes(n)
                }
            }
        }
    }
}
