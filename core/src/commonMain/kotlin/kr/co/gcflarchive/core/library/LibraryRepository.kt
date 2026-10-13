package kr.co.gcflarchive.core.library

import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.encPath
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.jsonOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** Thin wrappers over the site endpoints the archive web pages use. */
class LibraryRepository(private val site: SiteClient) {
    /** /api/documents lists 기출 and the PDF 자료실 together; callers split on [LibraryDocument.isPastExam]. */
    suspend fun documents(): List<LibraryDocument> = LibraryParser.documents(site.get("/api/documents"))

    suspend fun subjects(): SubjectConfig = LibraryParser.subjects(site.get("/api/past-exam-subjects"))

    /** Throws LoginRequiredException when logged out. */
    suspend fun favorites(): Set<String> = LibraryParser.favorites(site.get("/api/favorites"))

    /** Returns whether the exam is now starred. */
    suspend fun toggleFavorite(filename: String): Boolean =
        site.postForJson("/api/favorites", jsonOf("filename" to filename)).optBoolean("starred")

    suspend fun answerSheet(filename: String): AnswerSheet =
        LibraryParser.answerSheet(site.get("/api/answer-sheet/${encPath(filename)}"))

    /** Copies a past exam into 극플드라이브; returns the server's message. */
    suspend fun addExamToDrive(filename: String, includeAnswers: Boolean): String =
        site.postForJson("/api/share/add-exam", jsonOf("filename" to filename, "includeAnswers" to includeAnswers))
            .str("message") ?: "극플드라이브에 담았습니다."

    suspend fun photoAlbums(): List<PhotoAlbum> = LibraryParser.photoAlbums(site.get("/api/photo-albums"))

    suspend fun videos(): List<VideoEntry> = LibraryParser.videos(site.get("/api/videos"))

    suspend fun viewerInfo(filename: String, includeAnswers: Boolean): ViewerInfo {
        val j = site.getJson(viewerInfoPath(filename, includeAnswers))
        val pages = j.optJSONArray("pages")?.raw.orEmpty().mapNotNull { el ->
            val pair = el as? JsonArray ?: return@mapNotNull null
            val w = (pair.getOrNull(0) as? JsonPrimitive)?.content?.toFloatOrNull() ?: return@mapNotNull null
            val h = (pair.getOrNull(1) as? JsonPrimitive)?.content?.toFloatOrNull() ?: return@mapNotNull null
            w to h
        }
        return ViewerInfo(cleanTitle(j.optString("title")), pages)
    }

    /** One watermarked page image (needs the session for protected exams). */
    suspend fun viewerPage(filename: String, page: Int, includeAnswers: Boolean): ByteArray =
        site.bytes(viewerPagePath(filename, page, includeAnswers))

    companion object {
        fun downloadUrl(filename: String, includeAnswers: Boolean = true): String =
            Config.url("/download/${encPath(filename)}" + if (includeAnswers) "" else "?answers=0")

        /** Watermarked page images for the in-app viewer (/api/pdf-viewer). */
        fun viewerInfoPath(filename: String, includeAnswers: Boolean): String =
            "/api/pdf-viewer/${encPath(filename)}/info" + if (includeAnswers) "" else "?answers=0"

        fun viewerPagePath(filename: String, page: Int, includeAnswers: Boolean): String =
            "/api/pdf-viewer/${encPath(filename)}/page/$page.png" + if (includeAnswers) "" else "?answers=0"
    }
}

/** /api/pdf-viewer/<file>/info: title + page sizes (width, height) for layout. */
data class ViewerInfo(val title: String, val pages: List<Pair<Float, Float>>)
