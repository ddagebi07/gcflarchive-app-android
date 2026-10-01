package kr.co.gcflarchive.admin.features.accounts

import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import org.json.JSONObject

/** 특수 사용자 계정 (60000~99999). */
class CustomAccountsFragment : ListFragment() {
    override val searchHint get() = getString(R.string.hakbun_search_hint)
    override val fab get() = getString(R.string.custom_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.delete)

    override suspend fun load(): List<Row> {
        val list = fetch("/api/custom-accounts").json().optJSONArray("accounts").objects().sortedBy { it.str("hakbun") }
        setSummary(getString(R.string.custom_summary, list.size))
        return list.map { o ->
            Row(id = o.str("hakbun"), title = o.str("hakbun"), subtitle = o.str("name").ifBlank { null },
                meta = shortTime(o.str("created_at")).ifBlank { null }, swipeable = true)
        }
    }

    override fun onFab() {
        FormSheet(requireActivity(), getString(R.string.custom_add), getString(R.string.custom_add_desc))
            .text("hakbun", getString(R.string.hakbun), number = true, required = true,
                validate = { if (it.toIntOrNull() in 60000..99999) null else getString(R.string.custom_range) })
            .text("name", getString(R.string.name))
            .text("password", getString(R.string.password), password = true, required = true)
            .show { v ->
                api.postJson("/api/custom-accounts", JSONObject().put("hakbun", v.text("hakbun")).put("name", v.text("name")).put("password", v.text("password")))
                reload()
            }
    }

    override fun onRowClick(row: Row) = onSwipe(row)

    override fun onSwipe(row: Row) {
        Dialogs.confirmSensitive(requireContext(), getString(R.string.custom_delete), getString(R.string.custom_delete_message, row.id), keyword = row.id) {
            act(getString(R.string.deleted)) { api.deleteJson("/api/custom-accounts/${row.id}") }
        }
    }
}
