package kr.co.gcflarchive.admin.features.content

/** Pure helpers for the 공지 screen (unit-tested). */
object NoticeAdmin {
    /** routes/content.py limits. */
    const val TITLE_MAX = 200
    const val TEXT_MAX = 20000
    const val PHOTOS_MAX = 10

    /** One-line preview from a notice's HTML (tags, entities and extra spaces removed). */
    fun preview(html: String, max: Int = 80): String {
        val text = html
            .replace(Regex("<(br|/p|/div|/li)\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&amp;", "&")
            .replace(Regex("\\s+"), " ").trim()
        return if (text.length > max) text.take(max).trimEnd() + "…" else text
    }

    /** Photo ids -> the paths the server serves them at. */
    fun imageUrl(id: String) = "/api/community/images/$id"

    fun titleError(title: String): String? =
        if (title.isBlank() || title.length > TITLE_MAX) "제목을 1~${TITLE_MAX}자로 입력해주세요." else null

    fun bodyError(text: String, photoCount: Int): String? = when {
        text.length > TEXT_MAX -> "내용은 ${"%,d".format(TEXT_MAX)}자까지 쓸 수 있어요."
        text.isBlank() && photoCount == 0 -> "내용을 입력하거나 사진을 붙여주세요."
        else -> null
    }
}
