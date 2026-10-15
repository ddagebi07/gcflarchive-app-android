package kr.co.gcflarchive.admin.features.community

import android.net.Uri
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.features.jsonArray
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject

/** 금칙어: 띄어쓰기·기호를 무시하고 찾아요. 추가는 여러 개를 한 번에, 삭제는 밀거나 여러 개 골라서. */
class CommunityFilterFragment : ListFragment() {
    override val searchHint get() = getString(R.string.community_filter_search)
    override val fab get() = getString(R.string.community_filter_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.delete)
    override val selectionAction get() = getString(R.string.delete)
    override val emptyText get() = getString(R.string.community_filter_empty)

    override suspend fun load(): List<Row> {
        val words = fetch("/api/community/admin/filter-words").json().optJSONArray("words").objects()
        setSummary(getString(R.string.community_filter_summary, words.size))
        return words.map { w ->
            Row(
                id = w.str("word"),
                title = w.str("word"),
                meta = joinMeta(w.str("createdBy"), CommunityAdmin.time(w.str("createdAt"))),
                swipeable = true,
                selectable = true,
            )
        }
    }

    override fun onFab() {
        FormSheet(requireActivity(), getString(R.string.community_filter_add), getString(R.string.community_filter_add_desc))
            .text("words", getString(R.string.community_filter_words), multiline = true, required = true)
            .submitText(getString(R.string.community_filter_add_submit))
            .show { v ->
                val words = CommunityAdmin.splitWords(v.text("words"))
                api.postJson("/api/community/admin/filter-words", JSONObject().put("words", jsonArray(words)))
                reload()
            }
    }

    override fun onSwipe(row: Row) = delete(listOf(row))
    override fun onSelectionAction(rows: List<Row>) = delete(rows)

    private fun delete(rows: List<Row>) {
        Dialogs.confirm(
            requireContext(),
            getString(R.string.community_filter_delete_title, rows.size),
            rows.joinToString(", ") { it.id },
            getString(R.string.delete),
        ) {
            endSelection()
            act(getString(R.string.deleted)) {
                rows.forEach { api.deleteJson("/api/community/admin/filter-words/${Uri.encode(it.id)}") }
            }
        }
    }
}
