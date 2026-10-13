package kr.co.gcflarchive.core.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RichTextTest {
    @Test
    fun paragraphsBreaksAndWhitespace() {
        val t = RichText.parse("<p>첫  문단\n이어짐</p><p>둘째<br>줄</p>")
        assertEquals("첫 문단 이어짐\n\n둘째\n줄", t.text)
    }

    @Test
    fun boldAndLinkRanges() {
        val t = RichText.parse("""안내: <b>중요</b> <a href="/notice">공지</a>""", "https://gcflarchive.co.kr/")
        assertEquals("안내: 중요 공지", t.text)
        val bold = t.spans.single { it.style == SpanStyleKind.Bold }
        assertEquals("중요", t.text.substring(bold.start, bold.end))
        val link = t.spans.single { it.style is SpanStyleKind.Link }
        assertEquals("https://gcflarchive.co.kr/notice", (link.style as SpanStyleKind.Link).url)
        assertEquals("공지", t.text.substring(link.start, link.end))
    }

    @Test
    fun lists() {
        val t = RichText.parse("<ul><li>가</li><li>나</li></ul><ol><li>하나</li><li>둘</li></ol>")
        assertEquals("• 가\n• 나\n\n1. 하나\n2. 둘", t.text)
    }

    @Test
    fun inlineColors() {
        val t = RichText.parse("""<span style="color: #f00; font-weight: 700">빨강</span>""")
        assertTrue(SpanStyleKind.Color(0xFFFF0000) in t.spans.map { it.style })
        assertTrue(SpanStyleKind.Bold in t.spans.map { it.style })
        assertEquals(0xFF0B3492, RichText.parseColor("rgb(11, 52, 146)"))
        assertEquals(null, RichText.parseColor("transparent"))
    }

    @Test
    fun entitiesAndNbsp() {
        assertEquals("A & B C", RichText.parse("A &amp; B&nbsp;C").text)
    }
}
