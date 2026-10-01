package kr.co.gcflarchive.admin.features.content

import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.features.strings
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONArray

/** 지도 리뷰: 최신순 목록, 스와이프·다중 선택 삭제. */
class MapReviewsFragment : ListFragment() {
    override val searchHint get() = getString(R.string.review_search_hint)
    override val swipeLabel get() = getString(R.string.delete)
    override val selectionAction get() = getString(R.string.delete)

    override suspend fun load(): List<Row> {
        val reviews = JSONArray(fetch("/api/admin/map/reviews").body).objects().sortedByDescending { it.str("timestamp") }
        setSummary(getString(R.string.review_summary, reviews.size))
        return reviews.map { r ->
            val rating = r.optInt("rating")
            Row(
                id = r.str("reviewId"),
                title = r.str("content").ifBlank { r.optJSONArray("hashtags").strings().joinToString(" ") { "#$it" } }.ifBlank { "(내용 없음)" },
                subtitle = joinMeta(r.str("placeName").ifBlank { r.str("placeId") }, r.str("nickname")),
                meta = joinMeta(shortTime(r.str("timestamp")), r.str("userId").ifBlank { r.str("hakbun") }),
                badges = listOfNotNull(if (rating > 0) "★".repeat(rating) to Badge.INFO else null),
                swipeable = true, selectable = true,
            )
        }
    }

    override fun onSwipe(row: Row) = delete(listOf(row))
    override fun onSelectionAction(rows: List<Row>) = delete(rows)

    private fun delete(rows: List<Row>) {
        Dialogs.confirmSensitive(requireContext(), getString(R.string.review_delete, rows.size), rows.joinToString("\n") { "· ${it.title.take(40)}" }, keyword = getString(R.string.delete)) {
            endSelection()
            act(getString(R.string.deleted)) { rows.forEach { api.deleteJson("/api/admin/map/reviews/${it.id}") } }
        }
    }
}
