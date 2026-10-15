package kr.co.gcflarchive.core.notice

import kr.co.gcflarchive.core.net.JArray
import kr.co.gcflarchive.core.net.JObject
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.objects
import kr.co.gcflarchive.core.net.parseArray

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
    fun parse(o: JObject) = Notice(
        id = o.optInt("id"),
        title = o.optString("title").ifBlank { "제목 없음" },
        author = o.optString("author").ifBlank { "관리자" },
        date = o.optString("date").ifBlank { "-" },
        views = o.optInt("views"),
        critical = o.optBoolean("isCritical"),
        contentHtml = o.optString("content"),
    )

    fun parseList(arr: JArray): List<Notice> = arr.objects().map(::parse)

    fun parsePushes(o: JObject): NoticePushFeed = NoticePushFeed(
        o.optString("now"),
        o.optJSONArray("notices").objects()
            .map { NoticePush(it.optInt("id"), it.optString("title").ifBlank { "새 공지" }, it.optBoolean("isCritical")) }
            .filter { it.id > 0 },
    )

    /** notice.html: title or author contains the query (case-insensitive). */
    fun filter(items: List<Notice>, query: String): List<Notice> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { q in it.title.lowercase() || q in it.author.lowercase() }
    }
}

class NoticeRepository(private val site: SiteClient) {
    suspend fun list(): List<Notice> = NoticeLogic.parseList(parseArray(site.get("/api/notices")))

    suspend fun detail(id: Int): Notice = NoticeLogic.parse(site.getJson("/api/notices/$id"))

    /** Pushed notices after [since] (null: only the server time, for the first check). */
    suspend fun pushes(since: String?): NoticePushFeed =
        NoticeLogic.parsePushes(site.getJson("/api/notices/pushes" + if (since.isNullOrBlank()) "" else "?since=" + kr.co.gcflarchive.core.encQuery(since)))

    /**
     * One check for 공지 알림: returns the notices to announce (at most [max]) and remembers the
     * server time. The very first check only remembers the time, so a new install isn't flooded.
     */
    suspend fun newPushes(prefs: kr.co.gcflarchive.core.prefs.AppPrefs, max: Int = 3): List<NoticePush> {
        if (!prefs.noticeNotifyEnabled) return emptyList()
        val since = prefs.noticePushSince
        val feed = pushes(since)
        if (feed.now.isNotBlank()) prefs.noticePushSince = feed.now
        return if (since == null) emptyList() else feed.notices.take(max)
    }
}
