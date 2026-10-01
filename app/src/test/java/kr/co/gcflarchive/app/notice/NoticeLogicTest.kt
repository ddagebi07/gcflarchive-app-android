package kr.co.gcflarchive.app.notice

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeLogicTest {
    @Test
    fun parseAndFilter() {
        val list = NoticeLogic.parseList(
            JSONArray("""[{"id":3,"title":"서버 점검","author":"관리자","isCritical":true,"views":5},{"id":2,"title":"","date":"2026-09-01"}]"""),
        )
        assertEquals("제목 없음", list[1].title)
        assertEquals("관리자", list[1].author)
        assertEquals(true, list[0].critical)
        assertEquals(listOf(3), NoticeLogic.filter(list, " 점검 ").map { it.id })
        assertEquals(2, NoticeLogic.filter(list, "관리자").size)
        assertEquals(2, NoticeLogic.filter(list, "").size)
    }
}
