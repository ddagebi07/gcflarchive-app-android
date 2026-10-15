package kr.co.gcflarchive.core.community

import kotlinx.datetime.Instant
import kr.co.gcflarchive.core.net.parseObject
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
    fun parsesPolls() {
        val post = CommunityParser.post(
            """{"id":5,"hasPoll":true,"poll":{"question":"종목은?","options":[{"text":"축구","votes":3},{"text":"농구","votes":1}],
               "multiple":false,"closesAt":"","closed":false,"myChoices":[0],"voterCount":4,"showResults":true,"canVote":true}}""",
        )
        val poll = post.poll!!
        assertTrue(post.summary.hasPoll)
        assertEquals(listOf("축구", "농구"), poll.options.map { it.text })
        assertEquals(setOf(0), poll.myChoices)
        assertEquals(75, poll.percent(0))
        assertEquals(25, poll.percent(1))
        val hidden = CommunityParser.poll(parseObject("""{"question":"q","options":[{"text":"a","votes":null},{"text":"b"}],"voterCount":0}"""))!!
        assertEquals(null, hidden.options[0].votes)
        assertEquals(0, hidden.percent(0))
        assertEquals(null, CommunityParser.poll(null))
    }

    @Test
    fun pollDraftFollowsServerRules() {
        assertEquals(null, PollDraft("종목은?", listOf("축구", "농구", " "), false).error())
        assertTrue(PollDraft("", listOf("a", "b"), false).error() != null)
        assertTrue(PollDraft("q", listOf("a", "a", ""), false).error() != null)
        assertTrue(PollDraft("q", (1..11).map { "$it" }, true).error() != null)
    }

    @Test
    fun parsesImagesAndRecentPosts() {
        val post = CommunityParser.post(
            """{"id":5,"images":[{"id":"ab12","url":"/api/community/images/ab12","width":1600,"height":800},{"id":""}],"imageCount":1}""",
        )
        assertEquals(listOf("ab12"), post.images.map { it.id })
        assertEquals(1600, post.images.single().width)
        assertEquals(1, post.summary.imageCount)
        val recent = CommunityParser.recent("""{"loggedIn":true,"posts":[{"id":7,"title":"안녕","boardName":"자유게시판"}]}""")
        assertTrue(recent.loggedIn)
        assertEquals("자유게시판", recent.posts.single().boardName)
        assertFalse(CommunityParser.recent("""{"loggedIn":false,"posts":[]}""").loggedIn)
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
