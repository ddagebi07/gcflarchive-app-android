package kr.co.gcflarchive.app.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CommunityParserTest {
    @Test
    fun parsesBoardsAndPage() {
        val boards = CommunityParser.boards(
            """{"boards":[{"id":"free","name":"자유게시판","description":"d","canWrite":true,"allowAnonymous":false,"postCount":3},{"id":""}]}""",
        )
        assertEquals(listOf("free"), boards.map { it.id })
        assertTrue(boards.single().canWrite)

        val page = CommunityParser.page(
            """{"page":2,"totalPages":5,"totalCount":90,"posts":[{"id":7,"title":"t","author":"하늘","createdAt":"2026-10-14T08:00:00Z","viewCount":4,"likeCount":2,"commentCount":1,"blinded":false,"isMine":true}]}""",
        )
        assertEquals(2, page.page)
        assertEquals(5, page.totalPages)
        assertEquals(7, page.posts.single().id)
        assertTrue(page.posts.single().isMine)
    }

    @Test
    fun parsesPostWithComments() {
        val post = CommunityParser.post(
            """{"id":3,"boardId":"info","title":"제목","author":"익명","createdAt":"2026-10-14T08:00:00Z","updatedAt":"2026-10-14T09:00:00Z",
               "content":"본문","liked":true,"canEdit":false,"viewCount":1,"likeCount":5,"commentCount":1,
               "comments":[{"id":9,"author":"바다","content":"좋아요","createdAt":"2026-10-14T08:10:00Z","updatedAt":"2026-10-14T08:10:00Z","blinded":false,"isMine":false}]}""",
        )
        assertEquals("info", post.boardId)
        assertTrue(post.liked)
        assertTrue(post.edited)
        assertFalse(post.comments.single().edited)
        assertEquals(5, post.summary.likeCount)
    }

    @Test
    fun errorsKeepTheServersWordsAndFlags() {
        val filtered = CommunityParser.error(422, """{"error":"쓸 수 없는 표현이 있어요: '바보'. 고쳐서 다시 올려주세요.","filtered":true}""")
        assertEquals("쓸 수 없는 표현이 있어요: '바보'. 고쳐서 다시 올려주세요.", filtered.message)
        assertTrue(CommunityParser.error(409, """{"error":"x","nicknameRequired":true}""").nicknameRequired)
        assertTrue(CommunityParser.error(401, "").loginRequired)
        assertEquals("인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요.", CommunityParser.error(502, "<html>").message)
    }

    @Test
    fun showsTimesInKoreanStyle() {
        val now = Instant.parse("2026-10-14T12:00:00Z")
        assertEquals("10월 14일 17:05", CommunityParser.displayTime("2026-10-14T08:05:00Z", now))
        assertEquals("2025년 3월 2일", CommunityParser.displayTime("2025-03-02T01:00:00Z", now))
        assertEquals("", CommunityParser.displayTime("not a date", now))
    }
}
