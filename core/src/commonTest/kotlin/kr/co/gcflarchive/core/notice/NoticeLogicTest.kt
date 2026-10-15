package kr.co.gcflarchive.core.notice

import kr.co.gcflarchive.core.net.parseArray
import kr.co.gcflarchive.core.net.parseObject
import kotlin.test.assertEquals
import kotlin.test.Test

class NoticeLogicTest {
    @Test
    fun parseAndFilter() {
        val list = NoticeLogic.parseList(
            parseArray("""[{"id":3,"title":"서버 점검","author":"관리자","isCritical":true,"views":5},{"id":2,"title":"","date":"2026-09-01"}]"""),
        )
        assertEquals("제목 없음", list[1].title)
        assertEquals("관리자", list[1].author)
        assertEquals(true, list[0].critical)
        assertEquals(listOf(3), NoticeLogic.filter(list, " 점검 ").map { it.id })
        assertEquals(2, NoticeLogic.filter(list, "관리자").size)
        assertEquals(2, NoticeLogic.filter(list, "").size)
    }

    @Test
    fun parsesPushFeed() {
        val feed = NoticeLogic.parsePushes(parseObject(
            """{"now":"2026-10-15T09:00:00Z","notices":[{"id":7,"title":"시험 안내","isCritical":true},{"id":0},{"id":8,"title":""}]}""",
        ))
        assertEquals("2026-10-15T09:00:00Z", feed.now)
        assertEquals(listOf(7, 8), feed.notices.map { it.id })
        assertEquals(true, feed.notices[0].critical)
        assertEquals("새 공지", feed.notices[1].title)
        assertEquals(0, NoticeLogic.parsePushes(parseObject("""{"now":"x"}""")).notices.size)
    }
}
