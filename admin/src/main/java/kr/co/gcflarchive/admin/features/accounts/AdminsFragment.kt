package kr.co.gcflarchive.admin.features.accounts

import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.core.Permission
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.jsonArray
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.features.strings
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import org.json.JSONObject

/** 관리자 계정: 학번·권한 칩 목록, 권한 체크리스트로 추가·수정, 삭제(이중 확인). */
class AdminsFragment : ListFragment() {
    override val searchHint get() = getString(R.string.hakbun_search_hint)
    override val fab get() = getString(R.string.admin_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.delete)

    override suspend fun load(): List<Row> {
        val admins = fetch("/api/admins").json().optJSONArray("admins").objects().sortedBy { it.str("hakbun") }
        setSummary(getString(R.string.admin_summary, admins.size))
        val me = AdminSession.get(requireContext()).hakbun
        return admins.map { o ->
            val perms = o.optJSONArray("permissions").strings()
            val ultimate = Permission.ULTIMATE.key in perms
            Row(
                id = o.str("hakbun"),
                title = o.str("hakbun") + if (o.str("hakbun") == me) " (나)" else "",
                meta = shortTime(o.str("created_at")).ifBlank { null },
                badges = if (ultimate) listOf("Ultimate" to Badge.PURPLE)
                else perms.map { key -> (Permission.of(key)?.label ?: key) to Badge.INFO },
                trailing = Trailing.Chevron,
                swipeable = true,
                payload = perms,
            )
        }
    }

    override fun onFab() = edit(null, emptyList())

    @Suppress("UNCHECKED_CAST")
    override fun onRowClick(row: Row) {
        if (guardWrite()) edit(row.id, row.payload as List<String>)
    }

    private fun edit(hakbun: String?, current: List<String>) {
        val options = Permission.ASSIGNABLE.map { it.key to "${it.label} (${it.key})" }
        val sheet = FormSheet(requireActivity(), getString(if (hakbun == null) R.string.admin_add else R.string.admin_edit, hakbun ?: ""))
        if (hakbun == null) {
            sheet.text("hakbun", getString(R.string.hakbun), number = true, required = true,
                validate = { if (it.length == 5 && it.all(Char::isDigit)) null else getString(R.string.hakbun_invalid) })
        }
        sheet.checklist("permissions", getString(R.string.admin_permissions), options, current.toSet())
            .note(getString(R.string.admin_permissions_note))
            .show { v ->
                val perms = v.list("permissions")
                require(perms.isNotEmpty()) { getString(R.string.admin_permissions_required) }
                val body = JSONObject().put("permissions", jsonArray(perms))
                // 권한 부여는 민감 작업: the server applies it immediately.
                if (hakbun == null) api.postJson("/api/admins", body.put("hakbun", v.text("hakbun")))
                else api.putJson("/api/admins/$hakbun", body)
                reload()
            }
    }

    override fun onSwipe(row: Row) {
        Dialogs.confirmSensitive(
            requireContext(), getString(R.string.admin_delete), getString(R.string.admin_delete_message, row.id), keyword = row.id,
        ) { act(getString(R.string.deleted)) { api.deleteJson("/api/admins/${row.id}") } }
    }
}
