package kr.co.gcflarchive.admin.features.content

import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing

/** 정답지: 기출 목록 → 편집기. */
class AnswerSheetsFragment : ListFragment() {
    override val searchHint get() = getString(R.string.doc_search_hint)

    override suspend fun load(): List<Row> {
        val docs = fetch("/api/admin/past-exam-documents").json().optJSONArray("documents").objects()
            .sortedByDescending { it.str("filename") }
        setSummary(getString(R.string.answer_summary, docs.size, docs.count { it.optBoolean("hasAnswerSheet") }))
        return docs.map { o ->
            Row(
                id = o.str("filename"),
                title = o.str("displayName").ifBlank { o.str("filename") },
                subtitle = o.str("collectionName").ifBlank { null },
                badges = listOf(if (o.optBoolean("hasAnswerSheet")) "답지 있음" to Badge.PURPLE else "답지 없음" to Badge.NEUTRAL),
                trailing = Trailing.Chevron,
            )
        }
    }

    override fun onRowClick(row: Row) {
        startActivity(AnswerSheetActivity.intent(requireContext(), row.id, row.title.toString()))
    }

    override fun onResume() {
        super.onResume()
        if (view != null) reload()
    }
}
