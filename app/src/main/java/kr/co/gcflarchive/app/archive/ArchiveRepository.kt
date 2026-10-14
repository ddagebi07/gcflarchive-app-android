package kr.co.gcflarchive.app.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.data.Http
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class Board(val code: String, val name: String, val postCount: Int)

data class Attachment(
    val filename: String,
    val fileSize: Long,
    val downloadPath: String,
    /** pdf | image | text | hwp | none (미리보기를 지원하지 않는 형식). */
    val previewKind: String = PreviewKinds.of(filename),
    /** /api/archive/preview/<board>/<idx>/<file> — append /info, /page/N.png or /raw. */
    val previewApi: String? = null,
    /** /archive/preview/... web page (HWP is shown there with the site's viewer). */
    val previewPage: String? = null,
) {
    val downloadUrl: String get() = Config.url(downloadPath)
    val canPreview: Boolean get() = previewKind != PreviewKinds.NONE && previewApi != null
}

/** Mirrors PREVIEW_KINDS in routes/archive_crawl.py. */
object PreviewKinds {
    const val PDF = "pdf"
    const val IMAGE = "image"
    const val TEXT = "text"
    const val HWP = "hwp"
    const val NONE = "none"

    private val byExtension = mapOf(
        "pdf" to PDF,
        "png" to IMAGE, "jpg" to IMAGE, "jpeg" to IMAGE, "gif" to IMAGE, "webp" to IMAGE, "bmp" to IMAGE,
        "txt" to TEXT, "csv" to TEXT, "md" to TEXT,
        "hwp" to HWP, "hwpx" to HWP,
    )

    fun of(filename: String): String =
        byExtension[filename.substringAfterLast('.', "").lowercase()] ?: NONE
}

data class PostSummary(
    val boardCode: String,
    val boardName: String,
    val idx: String,
    val title: String,
    val author: String,
    val postedAt: String,
    /** FTS snippet; matched terms are wrapped in <mark> tags. Empty when not searching. */
    val snippet: String,
    val attachmentCount: Int,
)

data class SearchPage(val totalCount: Int, val page: Int, val totalPages: Int, val items: List<PostSummary>)

data class PostRef(val idx: String, val title: String)

data class PostDetail(
    val boardCode: String,
    val boardName: String,
    val idx: String,
    val title: String,
    val author: String,
    val postedAt: String,
    val contentHtml: String,
    val originalUrl: String?,
    /** The archive's own link to this post (shared instead of the school site's URL). */
    val shareUrl: String,
    val attachments: List<Attachment>,
    val prev: PostRef?,
    val next: PostRef?,
)

enum class SearchSort(val apiValue: String) { RELEVANCE("relevance"), DATE("date") }

data class SearchQuery(
    val q: String = "",
    val board: String? = null,
    val sort: SearchSort = SearchSort.RELEVANCE,
    val attachmentsOnly: Boolean = false,
)

/**
 * Client for the public school-homepage archive API in routes/archive_crawl.py
 * (the same endpoints search.html uses). No login required.
 */
object ArchiveRepository {
    const val PAGE_SIZE = 20

    suspend fun boards(): List<Board> = getJson(Config.url("/api/archive/boards")).let(::parseBoards)

    suspend fun search(query: SearchQuery, page: Int): SearchPage {
        val url = Config.url("/api/archive/search").toHttpUrl().newBuilder().apply {
            if (query.q.isNotBlank()) addQueryParameter("q", query.q.trim())
            query.board?.let { addQueryParameter("board", it) }
            if (query.attachmentsOnly) addQueryParameter("has_attachment", "true")
            addQueryParameter("sort", query.sort.apiValue)
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
        }.build().toString()
        return parseSearch(getJson(url))
    }

    suspend fun detail(boardCode: String, idx: String): PostDetail =
        parseDetail(getJson(Config.url("/api/archive/posts/${enc(boardCode)}/${enc(idx)}")))

    private fun enc(segment: String) = android.net.Uri.encode(segment)

    private suspend fun getJson(url: String): String = withContext(Dispatchers.IO) {
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IOException(msg?.ifBlank { null } ?: "HTTP ${res.code}")
            }
            body
        }
    }

    internal fun parseBoards(body: String): List<Board> {
        val arr = JSONObject(body).optJSONArray("boards") ?: return emptyList()
        return arr.objects().map {
            Board(it.optString("board_code"), it.optString("board_name"), it.optInt("post_count"))
        }.filter { it.code.isNotBlank() }
    }

    internal fun parseSearch(body: String): SearchPage {
        val json = JSONObject(body)
        val items = (json.optJSONArray("items") ?: JSONArray()).objects().map {
            PostSummary(
                boardCode = it.optString("board_code"),
                boardName = it.optString("board_name"),
                idx = it.optString("idx"),
                title = it.optString("title"),
                author = it.optStringOrNull("author") ?: "학교",
                postedAt = it.optString("posted_at"),
                snippet = it.optStringOrNull("snippet") ?: "",
                attachmentCount = it.optInt("attachment_count"),
            )
        }
        return SearchPage(
            totalCount = json.optInt("total_count"),
            page = json.optInt("page", 1),
            totalPages = json.optInt("total_pages", 1),
            items = items,
        )
    }

    internal fun parseDetail(body: String): PostDetail {
        val json = JSONObject(body)
        fun ref(key: String) = json.optJSONObject(key)?.let { PostRef(it.optString("idx"), it.optString("title")) }
        return PostDetail(
            boardCode = json.optString("board_code"),
            boardName = json.optString("board_name"),
            idx = json.optString("idx"),
            title = json.optString("title"),
            author = json.optStringOrNull("author") ?: "학교",
            postedAt = json.optString("posted_at"),
            contentHtml = json.optStringOrNull("content_html") ?: json.optString("content_text"),
            originalUrl = json.optStringOrNull("original_url"),
            shareUrl = json.optStringOrNull("share_url") ?: shareUrl(json.optString("board_code"), json.optString("idx")),
            attachments = (json.optJSONArray("attachments") ?: JSONArray()).objects().map {
                val name = it.optString("filename")
                val api = it.optStringOrNull("preview_api")
                    ?: previewPath("/api/archive/preview", json.optString("board_code"), json.optString("idx"), name)
                Attachment(
                    filename = name,
                    fileSize = it.optLong("file_size"),
                    downloadPath = it.optString("download_url"),
                    previewKind = it.optStringOrNull("preview_kind") ?: PreviewKinds.of(name),
                    previewApi = api,
                    previewPage = it.optStringOrNull("preview_url")
                        ?: previewPath("/archive/preview", json.optString("board_code"), json.optString("idx"), name),
                )
            }.filter { it.downloadPath.isNotBlank() },
            prev = ref("prev_post"),
            next = ref("next_post"),
        )
    }

    /**
     * Splits an FTS snippet into (text, highlighted) runs. Only the <mark> tags the
     * engine inserts are interpreted; everything else is shown as literal text.
     */
    fun snippetRuns(snippet: String): List<Pair<String, Boolean>> {
        val runs = mutableListOf<Pair<String, Boolean>>()
        var rest = snippet
        val open = Regex("""<mark[^>]*>""")
        while (rest.isNotEmpty()) {
            val start = open.find(rest)
            if (start == null) {
                runs += rest to false
                break
            }
            if (start.range.first > 0) runs += rest.substring(0, start.range.first) to false
            val afterOpen = rest.substring(start.range.last + 1)
            val end = afterOpen.indexOf("</mark>")
            if (end < 0) {
                runs += afterOpen to true
                break
            }
            runs += afterOpen.substring(0, end) to true
            rest = afterOpen.substring(end + "</mark>".length)
        }
        return runs.filter { it.first.isNotEmpty() }
    }

    /** "2025-03-10 09:00:00" → "2025.03.10" */
    /** Same link the website's 공유 button copies: /search?s=<board>:<idx>. */
    fun shareUrl(boardCode: String, idx: String): String =
        Config.url("/search?s=" + java.net.URLEncoder.encode("$boardCode:$idx", "UTF-8"))

    private fun previewPath(prefix: String, boardCode: String, idx: String, filename: String): String? {
        if (boardCode.isBlank() || idx.isBlank() || filename.isBlank()) return null
        return "$prefix/${pathSegment(boardCode)}/${pathSegment(idx)}/${pathSegment(filename)}"
    }

    // Plain JVM encoding (android.net.Uri isn't available in unit tests).
    private fun pathSegment(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun displayDate(postedAt: String): String = postedAt.take(10).replace('-', '.')

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }
}
