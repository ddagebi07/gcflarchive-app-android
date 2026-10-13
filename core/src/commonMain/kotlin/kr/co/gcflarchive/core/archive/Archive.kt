package kr.co.gcflarchive.core.archive

import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.encPath
import kr.co.gcflarchive.core.encQuery
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.objects
import kr.co.gcflarchive.core.net.parseObject

data class Board(val code: String, val name: String, val postCount: Int)

data class Attachment(val filename: String, val fileSize: Long, val downloadPath: String) {
    val downloadUrl: String get() = Config.url(downloadPath)
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
                attachments = json.optJSONArray("attachments").objects()
                    .map { Attachment(it.optString("filename"), it.optLong("file_size"), it.optString("download_url")) }
                    .filter { it.downloadPath.isNotBlank() },
                prev = ref("prev_post"),
                next = ref("next_post"),
            )
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
