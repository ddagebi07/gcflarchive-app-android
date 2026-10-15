package kr.co.gcflarchive.core.community

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityParserTest {
    @Test
    fun parsesBoardsPageAndPost() {
        val boards = CommunityParser.boards("""{"boards":[{"id":"free","name":"자유게시판","canWrite":true,"postCount":3},{"id":""}]}""")
        assertEquals(listOf("free"), boards.map { it.id })
        val page = CommunityParser.page("""{"page":2,"totalPages":5,"totalCount":90,"posts":[{"id":7,"title":"t","likeCount":2,"isMine":true}]}""")
        assertEquals(2, page.page)
        assertTrue(page.posts.single().isMine)
        val post = CommunityParser.post(
            """{"id":3,"boardId":"info","title":"제목","createdAt":"2026-10-14T08:00:00Z","updatedAt":"2026-10-14T09:00:00Z","liked":true,
               "comments":[{"id":9,"content":"좋아요","createdAt":"2026-10-14T08:10:00Z","updatedAt":"2026-10-14T08:10:00Z"}]}""",
        )
        assertTrue(post.edited)
        assertFalse(post.comments.single().edited)
        assertEquals("info", post.boardId)
        assertTrue(post.canComment)  // older servers don't send it
        assertFalse(CommunityParser.post("""{"id":4,"canComment":false}""").canComment)
    }

    @Test
    fun errorsKeepTheServersWordsAndFlags() {
        assertEquals("쓸 수 없는 표현이 있어요: '바보'.", CommunityParser.error(422, """{"error":"쓸 수 없는 표현이 있어요: '바보'."}""").message)
        assertTrue(CommunityParser.error(409, """{"error":"x","nicknameRequired":true}""").nicknameRequired)
        assertTrue(CommunityParser.error(401, "").loginRequired)
    }

    @Test
    fun showsTimesInKoreanStyle() {
        val now = Instant.parse("2026-10-14T12:00:00Z")
        assertEquals("10월 14일 17:05", CommunityParser.displayTime("2026-10-14T08:05:00Z", now))
        assertEquals("2025년 3월 2일", CommunityParser.displayTime("2025-03-02T01:00:00Z", now))
        assertEquals("", CommunityParser.displayTime("nope", now))
    }
}
