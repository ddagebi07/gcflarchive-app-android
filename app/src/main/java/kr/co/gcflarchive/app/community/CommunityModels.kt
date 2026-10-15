package kr.co.gcflarchive.app.community

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CommunityMe(val loggedIn: Boolean, val nickname: String, val canModerate: Boolean)

data class CommunityBoard(
    val id: String,
    val name: String,
    val description: String,
    val canWrite: Boolean,
    val allowAnonymous: Boolean,
    val postCount: Int,
)

data class CommunityPostSummary(
    val id: Int,
    val title: String,
    val author: String,
    val createdAt: String,
    val viewCount: Int,
    val likeCount: Int,
    val commentCount: Int,
    val blinded: Boolean,
    val isMine: Boolean,
    val imageCount: Int = 0,
    /** Only on /api/community/recent (홈 화면). */
    val boardName: String = "",
)

/** A photo attached to a post; [url] is a site path that needs the session cookie. */
data class CommunityImage(val id: String, val url: String, val width: Int, val height: Int) : java.io.Serializable

/** 홈 화면 카드: newest posts from boards this user can read. */
data class CommunityRecent(val loggedIn: Boolean, val posts: List<CommunityPostSummary>)

data class CommunityPostPage(val page: Int, val totalPages: Int, val totalCount: Int, val posts: List<CommunityPostSummary>)

data class CommunityComment(
    val id: Int,
    val author: String,
    val content: String,
    val createdAt: String,
    val edited: Boolean,
    val blinded: Boolean,
    val isMine: Boolean,
)

data class CommunityPost(
    val summary: CommunityPostSummary,
    val boardId: String,
    val content: String,
    val liked: Boolean,
    val canEdit: Boolean,
    /** The board's 댓글 permission (servers without it: true). */
    val canComment: Boolean = true,
    val edited: Boolean,
    val comments: List<CommunityComment>,
    val images: List<CommunityImage> = emptyList(),
)

enum class CommunitySort(val apiValue: String, val label: String) {
    NEW("new", "최신순"),
    TOP("top", "추천순"),
}

/**
 * A refused community request. The server's message is already written for people
 * (e.g. "쓸 수 없는 표현이 있어요: '…'"), so screens show [message] as is.
 */
class CommunityException(
    val code: Int,
    message: String,
    val loginRequired: Boolean = false,
    val nicknameRequired: Boolean = false,
) : IOException(message)

/** Parsing for routes/community.py. Pure (org.json only) so it runs in unit tests. */
object CommunityParser {
    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    private val SAME_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
    private val OTHER_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일")

    fun me(body: String): CommunityMe = JSONObject(body).let {
        CommunityMe(it.optBoolean("loggedIn"), it.optString("nickname"), it.optBoolean("canModerate"))
    }

    fun boards(body: String): List<CommunityBoard> =
        JSONObject(body).optJSONArray("boards").objects().map {
            CommunityBoard(
                id = it.optString("id"),
                name = it.optString("name"),
                description = it.optString("description"),
                canWrite = it.optBoolean("canWrite"),
                allowAnonymous = it.optBoolean("allowAnonymous"),
                postCount = it.optInt("postCount"),
            )
        }.filter { it.id.isNotBlank() }

    fun summary(j: JSONObject) = CommunityPostSummary(
        id = j.optInt("id"),
        title = j.optString("title"),
        author = j.optString("author"),
        createdAt = j.optString("createdAt"),
        viewCount = j.optInt("viewCount"),
        likeCount = j.optInt("likeCount"),
        commentCount = j.optInt("commentCount"),
        blinded = j.optBoolean("blinded"),
        isMine = j.optBoolean("isMine"),
        imageCount = j.optInt("imageCount"),
        boardName = j.optString("boardName"),
    )

    fun image(j: JSONObject) = CommunityImage(j.optString("id"), j.optString("url"), j.optInt("width"), j.optInt("height"))

    fun recent(body: String): CommunityRecent = JSONObject(body).let { j ->
        CommunityRecent(j.optBoolean("loggedIn"), j.optJSONArray("posts").objects().map(::summary))
    }

    fun page(body: String): CommunityPostPage = JSONObject(body).let { j ->
        CommunityPostPage(
            page = j.optInt("page", 1),
            totalPages = j.optInt("totalPages", 1),
            totalCount = j.optInt("totalCount"),
            posts = j.optJSONArray("posts").objects().map(::summary),
        )
    }

    fun post(body: String): CommunityPost = JSONObject(body).let { j ->
        CommunityPost(
            summary = summary(j),
            boardId = j.optString("boardId"),
            content = j.optString("content"),
            liked = j.optBoolean("liked"),
            canEdit = j.optBoolean("canEdit"),
            canComment = j.optBoolean("canComment", true),
            edited = isEdited(j.optString("createdAt"), j.optString("updatedAt")),
            comments = j.optJSONArray("comments").objects().map {
                CommunityComment(
                    id = it.optInt("id"),
                    author = it.optString("author"),
                    content = it.optString("content"),
                    createdAt = it.optString("createdAt"),
                    edited = isEdited(it.optString("createdAt"), it.optString("updatedAt")),
                    blinded = it.optBoolean("blinded"),
                    isMine = it.optBoolean("isMine"),
                )
            },
            images = j.optJSONArray("images").objects().map(::image).filter { it.id.isNotBlank() },
        )
    }

    /** Error body → exception with the server's own wording and its flags. */
    fun error(code: Int, body: String): CommunityException {
        val j = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
        val message = j.optString("error").ifBlank {
            if (code == 401) "로그인하면 이용할 수 있어요." else "인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요."
        }
        return CommunityException(code, message, loginRequired = code == 401 || j.optBoolean("loginRequired"), nicknameRequired = j.optBoolean("nicknameRequired"))
    }

    private fun isEdited(created: String, updated: String) = updated.isNotBlank() && created.isNotBlank() && updated > created

    /** "10월 14일 17:05" this year, "2025년 3월 2일" otherwise. */
    fun displayTime(iso: String, now: Instant = Instant.now()): String {
        val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
        val t = instant.atZone(SEOUL)
        return if (t.year == now.atZone(SEOUL).year) t.format(SAME_YEAR) else t.format(OTHER_YEAR)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
