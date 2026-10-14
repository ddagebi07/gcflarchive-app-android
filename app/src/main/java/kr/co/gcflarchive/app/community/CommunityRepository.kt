package kr.co.gcflarchive.app.community

import kr.co.gcflarchive.app.data.SiteApi
import org.json.JSONObject

/** /api/community/… (routes/community.py), with the site session cookie. */
object CommunityRepository {
    suspend fun me(): CommunityMe = CommunityParser.me(ok(SiteApi.exchange("/api/community/me")))

    suspend fun boards(): List<CommunityBoard> = CommunityParser.boards(ok(SiteApi.exchange("/api/community/boards")))

    suspend fun posts(boardId: String, page: Int, sort: CommunitySort, query: String): CommunityPostPage {
        val q = if (query.isBlank()) "" else "&q=" + enc(query)
        return CommunityParser.page(ok(SiteApi.exchange("/api/community/boards/${enc(boardId)}/posts?page=$page&sort=${sort.apiValue}$q")))
    }

    suspend fun post(id: Int): CommunityPost = CommunityParser.post(ok(SiteApi.exchange("/api/community/posts/$id")))

    suspend fun write(boardId: String, title: String, content: String, anonymous: Boolean): Int {
        val body = JSONObject().put("title", title).put("content", content).put("anonymous", anonymous)
        return JSONObject(ok(SiteApi.exchange("/api/community/boards/${enc(boardId)}/posts", "POST", body))).optInt("id")
    }

    suspend fun edit(id: Int, title: String, content: String) {
        ok(SiteApi.exchange("/api/community/posts/$id", "PUT", JSONObject().put("title", title).put("content", content)))
    }

    suspend fun delete(id: Int) {
        ok(SiteApi.exchange("/api/community/posts/$id", "DELETE"))
    }

    /** 추천 (again = 취소). Returns (liked, likeCount). */
    suspend fun toggleLike(id: Int): Pair<Boolean, Int> {
        val j = JSONObject(ok(SiteApi.exchange("/api/community/posts/$id/like", "POST")))
        return j.optBoolean("liked") to j.optInt("likeCount")
    }

    suspend fun comment(postId: Int, content: String) {
        ok(SiteApi.exchange("/api/community/posts/$postId/comments", "POST", JSONObject().put("content", content)))
    }

    suspend fun editComment(id: Int, content: String) {
        ok(SiteApi.exchange("/api/community/comments/$id", "PUT", JSONObject().put("content", content)))
    }

    suspend fun deleteComment(id: Int) {
        ok(SiteApi.exchange("/api/community/comments/$id", "DELETE"))
    }

    /** Returns true when this report hid the post or comment for review. */
    suspend fun report(targetType: String, targetId: Int, reason: String): Boolean {
        val body = JSONObject().put("targetType", targetType).put("targetId", targetId).put("reason", reason)
        return JSONObject(ok(SiteApi.exchange("/api/community/report", "POST", body))).optBoolean("blinded")
    }

    private fun ok(res: SiteApi.Response): String {
        if (!res.isSuccessful) throw CommunityParser.error(res.code, res.body)
        return res.body
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
