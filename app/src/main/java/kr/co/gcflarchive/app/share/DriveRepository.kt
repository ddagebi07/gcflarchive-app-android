package kr.co.gcflarchive.app.share

import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.data.SiteApi
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

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
        val percent = ((bytes * 100.0) / MAX_BYTES).let { Math.round(it).toInt() }.coerceIn(0, 100)
        return DriveUsage(personal.size, bytes, percent)
    }

    fun status(file: DriveFile, now: Instant = Instant.now()): DriveFileStatus {
        if (!file.downloadedAt.isNullOrBlank()) {
            val ms = parse(file.deleteAt)?.let { Duration.between(now, it).toMillis() } ?: 0
            return DriveFileStatus.Downloaded(ceilDiv(ms, 60_000))
        }
        val ms = parse(file.expiresAt)?.let { Duration.between(now, it).toMillis() } ?: 0
        return DriveFileStatus.Stored(ceilDiv(ms, 86_400_000))
    }

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all(Char::isDigit)

    /** The public drive address a shared PC opens: https://gcflarchive.co.kr/<학번>-<PIN>. */
    fun driveUrl(userId: String, pin: String): String = Config.url("/$userId-$pin")

    /** Python isoformat()+"Z" timestamps, with or without fractional seconds. */
    fun parse(iso: String?): Instant? = iso?.takeIf { it.isNotBlank() }?.let {
        runCatching { Instant.parse(if (it.endsWith("Z") || it.contains('+')) it else it + "Z") }.getOrNull()
    }

    private fun ceilDiv(ms: Long, unit: Long): Long = if (ms <= 0) 0 else (ms + unit - 1) / unit

    fun parseListing(json: JSONObject): DriveListing {
        val arr = json.optJSONArray("files")
        val files = (0 until (arr?.length() ?: 0)).map { i ->
            val o = arr!!.getJSONObject(i)
            DriveFile(
                fileId = o.optString("file_id"),
                filename = o.optString("filename"),
                fileSize = o.optLong("file_size"),
                uploadedAt = o.optString("uploaded_at"),
                expiresAt = o.optString("expires_at"),
                shortCode = o.optString("short_code"),
                downloadedAt = o.optString("downloaded_at").takeIf { o.has("downloaded_at") && !o.isNull("downloaded_at") && it.isNotBlank() },
                deleteAt = o.optString("delete_at").takeIf { o.has("delete_at") && !o.isNull("delete_at") && it.isNotBlank() },
                quotaExempt = o.optBoolean("quota_exempt"),
            )
        }
        return DriveListing(files, json.optString("pin").ifBlank { "0000" })
    }
}

/** 극플드라이브 APIs (routes/temp_share.py); 401 surfaces as LoginRequiredException. */
object DriveRepository {
    suspend fun list(): DriveListing = DriveLogic.parseListing(JSONObject(SiteApi.get("/api/share/list")))

    suspend fun setPin(pin: String) {
        SiteApi.postJson("/api/share/pin", JSONObject().put("pin", pin))
    }

    suspend fun delete(fileId: String) {
        SiteApi.postJson("/api/share/delete/$fileId", JSONObject())
    }
}
