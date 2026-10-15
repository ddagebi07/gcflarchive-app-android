package kr.co.gcflarchive.core.community

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kr.co.gcflarchive.core.encPath
import kr.co.gcflarchive.core.encQuery
import kr.co.gcflarchive.core.net.JObject
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.UploadFile
import kr.co.gcflarchive.core.net.objects
import kr.co.gcflarchive.core.net.parseObject

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
data class CommunityImage(val id: String, val url: String, val width: Int, val height: Int)

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

/** Reasons offered when reporting (the server stores the text as is). */
val COMMUNITY_REPORT_REASONS = listOf("욕설이나 비하", "음란하거나 불쾌한 내용", "광고나 도배", "개인정보 노출", "그 밖의 문제")

/** A refused request; [message] is the server's own wording, written for people. */
class CommunityException(
    val code: Int,
    message: String,
    val loginRequired: Boolean = false,
    val nicknameRequired: Boolean = false,
) : Exception(message)

/** Parsing for routes/community.py (shared with the Android app's CommunityParser). */
object CommunityParser {
    fun me(body: String): CommunityMe = parseObject(body).let {
        CommunityMe(it.optBoolean("loggedIn"), it.optString("nickname"), it.optBoolean("canModerate"))
    }

    fun boards(body: String): List<CommunityBoard> = parseObject(body).optJSONArray("boards").objects().map {
        CommunityBoard(
            id = it.optString("id"),
            name = it.optString("name"),
            description = it.optString("description"),
            canWrite = it.optBoolean("canWrite"),
            allowAnonymous = it.optBoolean("allowAnonymous"),
            postCount = it.optInt("postCount"),
        )
    }.filter { it.id.isNotBlank() }

    fun summary(j: JObject) = CommunityPostSummary(
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

    fun image(j: JObject) = CommunityImage(j.optString("id"), j.optString("url"), j.optInt("width"), j.optInt("height"))

    fun recent(body: String): CommunityRecent = parseObject(body).let { j ->
        CommunityRecent(j.optBoolean("loggedIn"), j.optJSONArray("posts").objects().map(::summary))
    }

    fun page(body: String): CommunityPostPage = parseObject(body).let { j ->
        CommunityPostPage(j.optInt("page", 1), j.optInt("totalPages", 1), j.optInt("totalCount"), j.optJSONArray("posts").objects().map(::summary))
    }

    fun post(body: String): CommunityPost = parseObject(body).let { j ->
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

    fun error(code: Int, body: String): CommunityException {
        val j = parseObject(body)
        val message = j.str("error") ?: if (code == 401) "로그인하면 이용할 수 있어요." else "인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요."
        return CommunityException(code, message, loginRequired = code == 401 || j.optBoolean("loginRequired"), nicknameRequired = j.optBoolean("nicknameRequired"))
    }

    private fun isEdited(created: String, updated: String) = updated.isNotBlank() && created.isNotBlank() && updated > created

    private const val SEOUL = "Asia/Seoul"

    /** "10월 14일 17:05" this year, "2025년 3월 2일" otherwise. */
    fun displayTime(iso: String, now: Instant): String {
        val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
        val zone = TimeZone.of(SEOUL)
        val t = instant.toLocalDateTime(zone)
        val two = { n: Int -> n.toString().padStart(2, '0') }
        return if (t.year == now.toLocalDateTime(zone).year) "${t.monthNumber}월 ${t.dayOfMonth}일 ${two(t.hour)}:${two(t.minute)}"
        else "${t.year}년 ${t.monthNumber}월 ${t.dayOfMonth}일"
    }
}

/** /api/community/… with the site session cookie. */
class CommunityRepository(private val site: SiteClient) {
    suspend fun me(): CommunityMe = CommunityParser.me(ok(site.exchange("/api/community/me")))

    suspend fun boards(): List<CommunityBoard> = CommunityParser.boards(ok(site.exchange("/api/community/boards")))

    suspend fun posts(boardId: String, page: Int, sort: CommunitySort, query: String): CommunityPostPage {
        val q = if (query.isBlank()) "" else "&q=" + encQuery(query.trim())
        return CommunityParser.page(ok(site.exchange("/api/community/boards/${encPath(boardId)}/posts?page=$page&sort=${sort.apiValue}$q")))
    }

    suspend fun post(id: Int): CommunityPost = CommunityParser.post(ok(site.exchange("/api/community/posts/$id")))

    suspend fun recent(limit: Int = 5): CommunityRecent = CommunityParser.recent(ok(site.exchange("/api/community/recent?limit=$limit")))

    /** Uploads one photo; attach it by passing its id to [write] or [edit]. */
    suspend fun uploadImage(file: UploadFile, onProgress: (Float) -> Unit = {}): CommunityImage {
        val res = site.upload("/api/community/images", "file", listOf(file), onProgress = onProgress)
        return CommunityParser.image(parseObject(ok(res)).optJSONObject("image") ?: throw CommunityParser.error(res.code, res.body))
    }

    suspend fun write(boardId: String, title: String, content: String, anonymous: Boolean, imageIds: List<String> = emptyList()): Int {
        val body = buildJsonObject {
            put("title", title); put("content", content); put("anonymous", anonymous)
            put("images", JsonArray(imageIds.map { JsonPrimitive(it) }))
        }
        return parseObject(ok(site.exchange("/api/community/boards/${encPath(boardId)}/posts", "POST", body))).optInt("id")
    }

    /** [imageIds] null keeps the photos as they are. */
    suspend fun edit(id: Int, title: String, content: String, imageIds: List<String>? = null) {
        val body = buildJsonObject {
            put("title", title); put("content", content)
            if (imageIds != null) put("images", JsonArray(imageIds.map { JsonPrimitive(it) }))
        }
        ok(site.exchange("/api/community/posts/$id", "PUT", body))
    }

    suspend fun delete(id: Int) {
        ok(site.exchange("/api/community/posts/$id", "DELETE"))
    }

    /** 추천 (again = 취소). Returns (liked, likeCount). */
    suspend fun toggleLike(id: Int): Pair<Boolean, Int> {
        val j = parseObject(ok(site.exchange("/api/community/posts/$id/like", "POST")))
        return j.optBoolean("liked") to j.optInt("likeCount")
    }

    suspend fun comment(postId: Int, content: String) {
        ok(site.exchange("/api/community/posts/$postId/comments", "POST", buildJsonObject { put("content", content) }))
    }

    suspend fun editComment(id: Int, content: String) {
        ok(site.exchange("/api/community/comments/$id", "PUT", buildJsonObject { put("content", content) }))
    }

    suspend fun deleteComment(id: Int) {
        ok(site.exchange("/api/community/comments/$id", "DELETE"))
    }

    /** Returns true when this report hid the post or comment for review. */
    suspend fun report(targetType: String, targetId: Int, reason: String): Boolean {
        val body = buildJsonObject {
            put("targetType", targetType)
            put("targetId", JsonPrimitive(targetId))
            put("reason", reason)
        }
        return parseObject(ok(site.exchange("/api/community/report", "POST", body))).optBoolean("blinded")
    }

    private fun ok(res: SiteClient.Response): String {
        if (!res.isSuccessful) throw CommunityParser.error(res.code, res.body)
        return res.body
    }
}
