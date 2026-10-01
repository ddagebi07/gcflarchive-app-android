package kr.co.gcflarchive.admin.features.security

import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import org.json.JSONObject

/** 학번 차단: 목록(사유·출처) / 차단 추가 / 해제. */
class HakbunBlocksFragment : ListFragment() {
    override val searchHint get() = getString(R.string.hakbun_search_hint)
    override val fab get() = getString(R.string.hakbun_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.unblock)
    override val emptyText get() = getString(R.string.hakbun_empty)

    override suspend fun load(): List<Row> {
        val list = fetch("/api/blocked-hakbuns").json().optJSONArray("blocked").objects()
        setSummary(getString(R.string.hakbun_summary, list.size))
        return list.map { o ->
            val env = o.str("source") == "env"
            Row(
                id = o.str("hakbun"),
                title = o.str("hakbun"),
                subtitle = o.str("reason").ifBlank { null },
                meta = if (env) getString(R.string.hakbun_env_note) else null,
                badges = listOf((if (env) "환경변수" else "파일") to (if (env) Badge.NEUTRAL else Badge.INFO)),
                swipeable = !env,
                payload = o,
            )
        }
    }

    override fun onRowClick(row: Row) {
        if (row.swipeable) onSwipe(row) else Dialogs.toast(requireContext(), getString(R.string.hakbun_env_note))
    }

    override fun onSwipe(row: Row) {
        Dialogs.confirm(requireContext(), getString(R.string.unblock), getString(R.string.hakbun_unblock_confirm, row.id), getString(R.string.unblock)) {
            act(getString(R.string.hakbun_unblocked, row.id)) { api.deleteJson("/api/blocked-hakbuns", JSONObject().put("hakbun", row.id)) }
        }
    }

    override fun onFab() {
        FormSheet(requireActivity(), getString(R.string.hakbun_add))
            .text("hakbun", getString(R.string.hakbun), number = true, required = true,
                validate = { if (it.length == 5 && it.all(Char::isDigit)) null else getString(R.string.hakbun_invalid) })
            .text("reason", getString(R.string.reason), multiline = true)
            .submitText(getString(R.string.block))
            .show { v ->
                api.postJson("/api/blocked-hakbuns", JSONObject().put("hakbun", v.text("hakbun")).put("reason", v.text("reason")))
                reload()
            }
    }
}
