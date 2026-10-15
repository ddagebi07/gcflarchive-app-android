package kr.co.gcflarchive.app.notice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticePushTest {
    @Test
    fun parsesPushFeed() {
        val feed = NoticeLogic.parsePushes(JSONObject(
            """{"now":"2026-10-15T09:00:00Z","notices":[{"id":7,"title":"시험 안내","isCritical":true},{"id":0},{"id":8,"title":""}]}""",
        ))
        assertEquals("2026-10-15T09:00:00Z", feed.now)
        assertEquals(listOf(7, 8), feed.notices.map { it.id })
        assertTrue(feed.notices[0].critical)
        assertEquals("새 공지", feed.notices[1].title)
        assertEquals(emptyList<NoticePush>(), NoticeLogic.parsePushes(JSONObject("""{"now":"x"}""")).notices)
    }
}
