package kr.co.gcflarchive.core.archive

import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.encPath
import kr.co.gcflarchive.core.encQuery
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.objects
import kr.co.gcflarchive.core.net.parseObject

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

    fun of(filename: String): String = byExtension[filename.substringAfterLast('.', "").lowercase()] ?: NONE
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

enum class SearchSort(val apiValue: String, val label: String) { RELEVANCE("relevance", "관련도순"), DATE("date", "최신순") }

data class SearchQuery(
    val q: String = "",
    val board: String? = null,
    val sort: SearchSort = SearchSort.RELEVANCE,
    val attachmentsOnly: Boolean = false,
)

/** Public school-homepage archive API (routes/archive_crawl.py, search.html). No login. */
class ArchiveRepository(private val site: SiteClient) {
    suspend fun boards(): List<Board> = parseBoards(site.get("/api/archive/boards"))

    suspend fun search(query: SearchQuery, page: Int): SearchPage {
        val params = buildList {
            if (query.q.isNotBlank()) add("q=" + encQuery(query.q.trim()))
            query.board?.let { add("board=" + encQuery(it)) }
            if (query.attachmentsOnly) add("has_attachment=true")
            add("sort=" + query.sort.apiValue)
            add("page=$page")
            add("limit=$PAGE_SIZE")
        }
        return parseSearch(site.get("/api/archive/search?" + params.joinToString("&")))
    }

    suspend fun detail(boardCode: String, idx: String): PostDetail =
        parseDetail(site.get("/api/archive/posts/${encPath(boardCode)}/${encPath(idx)}"))

    /** Page sizes (width, height) of a PDF attachment for [Attachment.previewApi]. */
    suspend fun previewPages(previewApi: String): List<Pair<Float, Float>> =
        parseObject(site.get("$previewApi/info")).optJSONArray("pages")?.raw.orEmpty().mapNotNull { page ->
            val pair = page as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
            val w = (pair.getOrNull(0) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()
            val h = (pair.getOrNull(1) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()
            if (w != null && h != null) w to h else null
        }

    /** Text attachments (txt / csv / md), decoded by the server. */
    suspend fun previewText(previewApi: String): String = site.get("$previewApi/raw")

    companion object {
        const val PAGE_SIZE = 20

        fun parseBoards(body: String): List<Board> = parseObject(body).optJSONArray("boards").objects()
            .map { Board(it.optString("board_code"), it.optString("board_name"), it.optInt("post_count")) }
            .filter { it.code.isNotBlank() }

        fun parseSearch(body: String): SearchPage {
            val json = parseObject(body)
            return SearchPage(
                totalCount = json.optInt("total_count"),
                page = json.optInt("page", 1),
                totalPages = json.optInt("total_pages", 1),
                items = json.optJSONArray("items").objects().map {
                    PostSummary(
                        boardCode = it.optString("board_code"),
                        boardName = it.optString("board_name"),
                        idx = it.optString("idx"),
                        title = it.optString("title"),
                        author = it.str("author") ?: "학교",
                        postedAt = it.optString("posted_at"),
                        snippet = it.str("snippet") ?: "",
                        attachmentCount = it.optInt("attachment_count"),
                    )
                },
            )
        }

        fun parseDetail(body: String): PostDetail {
            val json = parseObject(body)
            fun ref(key: String) = json.optJSONObject(key)?.let { PostRef(it.optString("idx"), it.optString("title")) }
            return PostDetail(
                boardCode = json.optString("board_code"),
                boardName = json.optString("board_name"),
                idx = json.optString("idx"),
                title = json.optString("title"),
                author = json.str("author") ?: "학교",
                postedAt = json.optString("posted_at"),
                contentHtml = json.str("content_html") ?: json.optString("content_text"),
                originalUrl = json.str("original_url"),
                shareUrl = json.str("share_url") ?: shareUrl(json.optString("board_code"), json.optString("idx")),
                attachments = json.optJSONArray("attachments").objects().map {
                    val name = it.optString("filename")
                    val board = json.optString("board_code")
                    val idx = json.optString("idx")
                    Attachment(
                        filename = name,
                        fileSize = it.optLong("file_size"),
                        downloadPath = it.optString("download_url"),
                        previewKind = it.str("preview_kind") ?: PreviewKinds.of(name),
                        previewApi = it.str("preview_api") ?: previewPath("/api/archive/preview", board, idx, name),
                        previewPage = it.str("preview_url") ?: previewPath("/archive/preview", board, idx, name),
                    )
                }.filter { it.downloadPath.isNotBlank() },
                prev = ref("prev_post"),
                next = ref("next_post"),
            )
        }

        /** Same link the website's 공유 button copies: /search?s=<board>:<idx>. */
        fun shareUrl(boardCode: String, idx: String): String = Config.url("/search?s=" + encQuery("$boardCode:$idx"))

        private fun previewPath(prefix: String, boardCode: String, idx: String, filename: String): String? {
            if (boardCode.isBlank() || idx.isBlank() || filename.isBlank()) return null
            return "$prefix/${encQuery(boardCode)}/${encQuery(idx)}/${encQuery(filename)}"
        }

        /** Splits an FTS snippet into (text, highlighted) runs; only <mark> is interpreted. */
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

    }
}
