package kr.co.gcflarchive.app

import android.net.Uri

object Config {
    const val BASE_URL = "https://gcflarchive.co.kr"
    const val HOST = "gcflarchive.co.kr"

    /** Appended to the WebView user agent so the server can tell app traffic apart. */
    val USER_AGENT_SUFFIX = "GCFLArchiveApp/${BuildConfig.VERSION_NAME}"

    // Same values meal.html uses for the NEIS open API.
    const val NEIS_API_KEY = "2087d61d2897469dbd2619d96224bbd0"
    const val NEIS_OFFICE_CODE = "J10"      // 경기도교육청
    const val NEIS_SCHOOL_CODE = "7530145"  // 과천외국어고등학교
    const val SCHOOL_NAME = "과천외국어고등학교"

    // Mirrors allowed_exts in routes/temp_share.py:api_upload_temp_file.
    val DRIVE_ALLOWED_EXTENSIONS = setOf(
        "pdf", "hwp", "hwpx", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "zip",
    )
    const val DRIVE_MAX_TOTAL_BYTES = 10L * 1024 * 1024

    fun url(path: String): String = BASE_URL + path

    fun loginUrl(next: String): String = url("/verify?next=" + Uri.encode(next))

    fun isOwnHost(uri: Uri?): Boolean {
        val host = uri?.host ?: return false
        return host == HOST || host.endsWith(".$HOST")
    }
}

/** Archive pages surfaced as shortcuts on the home tab. */
enum class ArchiveLink(val path: String, val titleRes: Int, val iconRes: Int) {
    PAST_EXAMS("/past-exams", R.string.link_past_exams, R.drawable.ic_exam),
    DOCUMENTS("/documents", R.string.link_documents, R.drawable.ic_pdf),
    PHOTOS("/photo", R.string.link_photos, R.drawable.ic_photo),
    VIDEOS("/video", R.string.link_videos, R.drawable.ic_video),
    SEARCH("/search", R.string.link_search, R.drawable.ic_search),
    GRADES("/grade-calculator", R.string.link_grades, R.drawable.ic_grade),
    MAP("/map", R.string.link_map, R.drawable.ic_map),
    NOTICE("/notice", R.string.link_notice, R.drawable.ic_notice),
}

/**
 * Site pages that have a native screen; everything else opens in the in-app WebView.
 * Used by the home tiles and by deep links / launcher shortcuts.
 */
object NativePages {
    fun intentFor(context: android.content.Context, path: String): android.content.Intent? {
        val cls = when (path.substringBefore('?').trimEnd('/')) {
            "/past-exams" -> kr.co.gcflarchive.app.library.PastExamsActivity::class.java
            "/documents" -> kr.co.gcflarchive.app.library.DocumentsActivity::class.java
            "/photo" -> kr.co.gcflarchive.app.library.PhotosActivity::class.java
            "/video" -> kr.co.gcflarchive.app.library.VideosActivity::class.java
            else -> return null
        }
        return android.content.Intent(context, cls)
    }
}
