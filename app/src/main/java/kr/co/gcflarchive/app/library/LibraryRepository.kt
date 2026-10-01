package kr.co.gcflarchive.app.library

import android.net.Uri
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.data.SiteApi
import org.json.JSONObject

/** Thin wrappers over the site endpoints the archive web pages use. */
object LibraryRepository {
    /** /api/documents lists 기출 and the PDF 자료실 together; callers split on [LibraryDocument.isPastExam]. */
    suspend fun documents(): List<LibraryDocument> = LibraryParser.documents(SiteApi.get("/api/documents"))

    suspend fun subjects(): SubjectConfig = LibraryParser.subjects(SiteApi.get("/api/past-exam-subjects"))

    /** Throws LoginRequiredException when logged out. */
    suspend fun favorites(): Set<String> = LibraryParser.favorites(SiteApi.get("/api/favorites"))

    /** Returns whether the exam is now starred. */
    suspend fun toggleFavorite(filename: String): Boolean =
        JSONObject(SiteApi.postJson("/api/favorites", JSONObject().put("filename", filename))).optBoolean("starred")

    suspend fun answerSheet(filename: String): AnswerSheet =
        LibraryParser.answerSheet(SiteApi.get("/api/answer-sheet/${Uri.encode(filename)}"))

    /** Copies a past exam into 극플드라이브; returns the server's message. */
    suspend fun addExamToDrive(filename: String, includeAnswers: Boolean): String =
        JSONObject(
            SiteApi.postJson(
                "/api/share/add-exam",
                JSONObject().put("filename", filename).put("includeAnswers", includeAnswers),
            ),
        ).optString("message", "극플드라이브에 담았습니다.")

    suspend fun photoAlbums(): List<PhotoAlbum> = LibraryParser.photoAlbums(SiteApi.get("/api/photo-albums"))

    suspend fun videos(): List<VideoEntry> = LibraryParser.videos(SiteApi.get("/api/videos"))

    fun downloadUrl(filename: String, includeAnswers: Boolean = true): String =
        Config.url("/download/${Uri.encode(filename)}" + if (includeAnswers) "" else "?answers=0")

    fun viewUrl(filename: String): String = Config.url("/view/${Uri.encode(filename)}")
}
