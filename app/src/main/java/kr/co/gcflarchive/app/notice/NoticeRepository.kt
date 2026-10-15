package kr.co.gcflarchive.app.notice

import kr.co.gcflarchive.app.data.SiteApi
import org.json.JSONArray
import org.json.JSONObject

/** 공지사항 of the site itself (routes/content.py: /api/notices). */
data class Notice(
    val id: Int,
    val title: String,
    val author: String,
    val date: String,
    val views: Int,
    val critical: Boolean,
    val contentHtml: String,
)

/** A notice the admin sent a push for (/api/notices/pushes). */
data class NoticePush(val id: Int, val title: String, val critical: Boolean)

/** [now] is the server time to send as `since` next time. */
data class NoticePushFeed(val now: String, val notices: List<NoticePush>)

object NoticeLogic {
    fun parse(o: JSONObject) = Notice(
        id = o.optInt("id"),
        title = o.optString("title").ifBlank { "제목 없음" },
        author = o.optString("author").ifBlank { "관리자" },
        date = o.optString("date").ifBlank { "-" },
        views = o.optInt("views"),
        critical = o.optBoolean("isCritical"),
        contentHtml = o.optString("content"),
    )

    fun parseList(arr: JSONArray): List<Notice> = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parse) }

    fun parsePushes(o: JSONObject): NoticePushFeed {
        val arr = o.optJSONArray("notices") ?: JSONArray()
        val pushes = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { NoticePush(it.optInt("id"), it.optString("title").ifBlank { "새 공지" }, it.optBoolean("isCritical")) }
        }.filter { it.id > 0 }
        return NoticePushFeed(o.optString("now"), pushes)
    }

    /** notice.html: title or author contains the query (case-insensitive). */
    fun filter(items: List<Notice>, query: String): List<Notice> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { q in it.title.lowercase() || q in it.author.lowercase() }
    }
}

object NoticeRepository {
    suspend fun list(): List<Notice> = NoticeLogic.parseList(JSONArray(SiteApi.get("/api/notices")))

    suspend fun detail(id: Int): Notice = NoticeLogic.parse(JSONObject(SiteApi.get("/api/notices/$id")))

    /** Pushed notices after [since] (null: only the server time, for the first check). */
    suspend fun pushes(since: String?): NoticePushFeed {
        val q = if (since.isNullOrBlank()) "" else "?since=" + java.net.URLEncoder.encode(since, "UTF-8")
        return NoticeLogic.parsePushes(JSONObject(SiteApi.get("/api/notices/pushes$q")))
    }
}
