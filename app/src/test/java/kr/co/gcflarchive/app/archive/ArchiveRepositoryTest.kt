package kr.co.gcflarchive.app.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveRepositoryTest {

    @Test
    fun parsesSearchPage() {
        val body = """
            {"query":"수학","total_count":42,"page":2,"limit":20,"total_pages":3,"items":[
              {"id":7,"idx":"1234","board_code":"notice","board_name":"공지사항","title":"2학기 수학 경시대회 안내",
               "author":null,"posted_at":"2026-09-15 09:00:00",
               "snippet":"...교내 <mark class=\"krds-highlight\">수학</mark> 경시대회를...","original_url":"https://www.gcfl.or.kr/x",
               "attachment_count":2,"attachments":[]}
            ]}
        """.trimIndent()

        val page = ArchiveRepository.parseSearch(body)

        assertEquals(42, page.totalCount)
        assertEquals(2, page.page)
        assertEquals(3, page.totalPages)
        val item = page.items.single()
        assertEquals("notice", item.boardCode)
        assertEquals("1234", item.idx)
        assertEquals("학교", item.author)
        assertEquals(2, item.attachmentCount)
        assertEquals("2026.09.15", ArchiveRepository.displayDate(item.postedAt))
    }

    @Test
    fun splitsSnippetHighlights() {
        val runs = ArchiveRepository.snippetRuns("...교내 <mark class=\"krds-highlight\">수학</mark> 경시 <mark>대회</mark>")
        assertEquals(
            listOf("...교내 " to false, "수학" to true, " 경시 " to false, "대회" to true),
            runs,
        )
        // Other angle brackets are literal text, not markup.
        assertEquals(listOf("a < b" to false), ArchiveRepository.snippetRuns("a < b"))
        assertEquals(emptyList<Pair<String, Boolean>>(), ArchiveRepository.snippetRuns(""))
    }

    @Test
    fun parsesDetailWithAttachmentsAndNav() {
        val body = """
            {"idx":"1234","board_code":"notice","board_name":"공지사항","title":"제목","author":"교무부",
             "posted_at":"2026-09-15","content_text":"본문","content_html":"<p>본문</p>","original_url":null,
             "attachments":[{"filename":"안내문.hwp","file_size":2048,"download_url":"/api/archive/download/notice/1234/%EC%95%88"}],
             "prev_post":{"idx":"1233","title":"이전","posted_at":"2026-09-10"},"next_post":null}
        """.trimIndent()

        val post = ArchiveRepository.parseDetail(body)

        assertEquals("<p>본문</p>", post.contentHtml)
        assertNull(post.originalUrl)
        assertEquals("안내문.hwp", post.attachments.single().filename)
        assertEquals("https://gcflarchive.co.kr/api/archive/download/notice/1234/%EC%95%88", post.attachments.single().downloadUrl)
        assertEquals("1233", post.prev?.idx)
        assertNull(post.next)
    }
}
