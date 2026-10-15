package kr.co.gcflarchive.admin.features.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeAdminTest {
    @Test
    fun previewStripsHtml() {
        assertEquals("1교시 <국어> 준비물 & 펜", NoticeAdmin.preview("<p>1교시 &lt;국어&gt;<br>준비물 &amp; 펜</p><p><img src=\"/a\"></p>"))
        assertEquals("가나다…", NoticeAdmin.preview("<p>가나다라마</p>", max = 3))
        assertEquals("", NoticeAdmin.preview(""))
    }

    @Test
    fun validatesForm() {
        assertTrue(NoticeAdmin.titleError(" ") != null)
        assertNull(NoticeAdmin.titleError("시험 안내"))
        assertTrue(NoticeAdmin.bodyError("", 0) != null)
        assertNull(NoticeAdmin.bodyError("", 1))
        assertNull(NoticeAdmin.bodyError("내용", 0))
        assertEquals("/api/community/images/ab", NoticeAdmin.imageUrl("ab"))
    }
}
