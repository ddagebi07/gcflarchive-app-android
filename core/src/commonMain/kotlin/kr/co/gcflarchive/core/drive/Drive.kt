package kr.co.gcflarchive.core.drive

import kotlin.math.roundToInt
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.encPath
import kr.co.gcflarchive.core.net.JObject
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.UploadFile
import kr.co.gcflarchive.core.net.jsonOf
import kr.co.gcflarchive.core.net.objects

/** One file in the user's 극플드라이브 (GET /api/share/list). */
data class DriveFile(
    val fileId: String,
    val filename: String,
    val fileSize: Long,
    val uploadedAt: String,
    val expiresAt: String,
    val shortCode: String,
    val downloadedAt: String?,
    val deleteAt: String?,
    /** 기출 copies added from the archive don't count toward the 5-file / 10MB limit. */
    val quotaExempt: Boolean,
) {
    val shortUrl: String get() = Config.url("/s/$shortCode")
}

data class DriveListing(val files: List<DriveFile>, val pin: String)

data class DriveUsage(val count: Int, val bytes: Long, val percent: Int) {
    val isFull: Boolean get() = count >= DriveLogic.MAX_FILES
}

sealed interface DriveFileStatus {
    /** Never downloaded: kept until [daysLeft] runs out (7 days after upload). */
    data class Stored(val daysLeft: Long) : DriveFileStatus

    /** Downloaded once: the server deletes it [minutesLeft] minutes from now. */
    data class Downloaded(val minutesLeft: Long) : DriveFileStatus
}

/** Pure rules mirrored from share.html, kept separate so they can be unit-tested. */
object DriveLogic {
    const val MAX_FILES = 5
    const val MAX_BYTES = 10L * 1024 * 1024

    fun usage(files: List<DriveFile>): DriveUsage {
        val personal = files.filterNot { it.quotaExempt }
        val bytes = personal.sumOf { it.fileSize.coerceAtLeast(0) }
        val percent = ((bytes * 100.0) / MAX_BYTES).roundToInt().coerceIn(0, 100)
        return DriveUsage(personal.size, bytes, percent)
    }

    fun status(file: DriveFile, now: Instant = Clock.System.now()): DriveFileStatus {
        if (!file.downloadedAt.isNullOrBlank()) {
            val ms = parse(file.deleteAt)?.let { (it - now).inWholeMilliseconds } ?: 0
            return DriveFileStatus.Downloaded(ceilDiv(ms, 60_000))
        }
        val ms = parse(file.expiresAt)?.let { (it - now).inWholeMilliseconds } ?: 0
        return DriveFileStatus.Stored(ceilDiv(ms, 86_400_000))
    }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    /** Client-side check before uploading; null when OK, else the message (share.html wording). */
    fun validate(files: List<Pair<String, Long>>): String? {
        if (files.isEmpty()) return "업로드할 파일이 없습니다."
        files.firstOrNull { extensionOf(it.first) !in Config.DRIVE_ALLOWED_EXTENSIONS }?.let {
            return "'${it.first}'은(는) 허용되지 않는 확장자입니다. (PDF·한글·오피스 문서·TXT·ZIP)"
        }
        if (files.sumOf { it.second.coerceAtLeast(0) } > MAX_BYTES) return "선택한 파일 전체 크기가 10MB를 넘습니다."
        return null
    }

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all(Char::isDigit)

    /** The public drive address a shared PC opens: https://gcflarchive.co.kr/<학번>-<PIN>. */
    fun driveUrl(userId: String, pin: String): String = Config.url("/$userId-$pin")

    /** Python isoformat()+"Z" timestamps, with or without fractional seconds. */
    fun parse(iso: String?): Instant? = iso?.takeIf { it.isNotBlank() }?.let {
        runCatching { Instant.parse(if (it.endsWith("Z") || it.contains('+')) it else it + "Z") }.getOrNull()
    }

    private fun ceilDiv(ms: Long, unit: Long): Long = if (ms <= 0) 0 else (ms + unit - 1) / unit

    fun parseListing(json: JObject): DriveListing {
        val files = json.optJSONArray("files").objects().map { o ->
            DriveFile(
                fileId = o.optString("file_id"),
                filename = o.optString("filename"),
                fileSize = o.optLong("file_size"),
                uploadedAt = o.optString("uploaded_at"),
                expiresAt = o.optString("expires_at"),
                shortCode = o.optString("short_code"),
                downloadedAt = o.str("downloaded_at"),
                deleteAt = o.str("delete_at"),
                quotaExempt = o.optBoolean("quota_exempt"),
            )
        }
        return DriveListing(files, json.optString("pin").ifBlank { "0000" })
    }
}

data class UploadedFile(val name: String, val shortCode: String) {
    val shortUrl: String get() = Config.url("/s/$shortCode")
}

sealed interface UploadResult {
    data class Success(val files: List<UploadedFile>, val zipped: Boolean) : UploadResult
    data object NeedLogin : UploadResult
    data class Failure(val message: String) : UploadResult
}

/** 극플드라이브 APIs (routes/temp_share.py); 401 surfaces as LoginRequiredException. */
class DriveRepository(private val site: SiteClient) {
    suspend fun list(): DriveListing = DriveLogic.parseListing(site.getJson("/api/share/list"))

    suspend fun setPin(pin: String) {
        site.postJson("/api/share/pin", jsonOf("pin" to pin))
    }

    suspend fun delete(fileId: String) {
        site.postJson("/api/share/delete/${encPath(fileId)}")
    }

    /** POST /api/share/upload (field "files"), same rules as share.html. */
    suspend fun upload(files: List<UploadFile>, onProgress: (Float) -> Unit = {}): UploadResult {
        DriveLogic.validate(files.map { it.name to it.bytes.size.toLong() })?.let { return UploadResult.Failure(it) }
        val res = runCatching { site.upload("/api/share/upload", "files", files, onProgress = onProgress) }
            .getOrElse { return UploadResult.Failure(it.message ?: "네트워크 오류가 발생했습니다.") }
        if (res.code == 401) return UploadResult.NeedLogin
        if (!res.isSuccessful) return UploadResult.Failure(res.error ?: "업로드에 실패했습니다. (HTTP ${res.code})")
        val j = res.json
        return UploadResult.Success(
            j.optJSONArray("files").objects().map { UploadedFile(it.optString("filename"), it.optString("short_code")) },
            j.optBoolean("zipped"),
        )
    }
}

