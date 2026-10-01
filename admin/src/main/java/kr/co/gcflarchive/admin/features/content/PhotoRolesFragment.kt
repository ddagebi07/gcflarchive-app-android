package kr.co.gcflarchive.admin.features.content

import kr.co.gcflarchive.admin.R
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

/** 사진첩 역할별 학번 배정 + 역할 관리. */
class PhotoRolesFragment : ListFragment() {
    override val searchHint get() = getString(R.string.hakbun_search_hint)
    override val fab get() = getString(R.string.role_assign) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.delete)
    private var roles: List<JSONObject> = emptyList()

    override suspend fun load(): List<Row> {
        val json = fetch("/api/admin/photo-access").json()
        roles = json.optJSONArray("roles").objects()
        val assignments = json.optJSONArray("assignments").objects()
        val rows = mutableListOf(Row.header(getString(R.string.role_list)))
        rows += roles.map { r ->
            Row("role:" + r.str("name"), r.str("label").ifBlank { r.str("name") }, r.str("description").ifBlank { null },
                meta = getString(R.string.role_members, assignments.count { a -> r.str("name") in a.optJSONArray("roles").strings() }),
                badges = listOf(r.str("name") to Badge.NEUTRAL), swipeable = true, payload = r)
        }
        rows += Row("role:add", getString(R.string.role_add), icon = R.drawable.ic_add, trailing = Trailing.Chevron)
        rows += Row.header(getString(R.string.role_assignments))
        rows += assignments.map { a ->
            Row("user:" + a.str("hakbun"), a.str("hakbun"), badges = a.optJSONArray("roles").strings().map { it to Badge.INFO },
                trailing = Trailing.Chevron, swipeable = true, payload = a)
        }
        setSummary(getString(R.string.role_summary, roles.size, assignments.size))
        return rows
    }

    override fun onFab() = assign(null)

    override fun onRowClick(row: Row) {
        if (!guardWrite()) return
        when {
            row.id == "role:add" -> FormSheet(requireActivity(), getString(R.string.role_add))
                .text("name", getString(R.string.role_name), required = true, hint = getString(R.string.role_name_hint))
                .text("label", getString(R.string.role_label))
                .text("description", getString(R.string.memo))
                .show { v ->
                    api.postJson("/api/admin/photo-access/roles", JSONObject().put("name", v.text("name")).put("label", v.text("label")).put("description", v.text("description")))
                    reload()
                }
            row.id.startsWith("user:") -> assign(row.payload as JSONObject)
        }
    }

    private fun assign(a: JSONObject?) {
        val sheet = FormSheet(requireActivity(), getString(R.string.role_assign))
        if (a == null) sheet.text("hakbun", getString(R.string.hakbun), number = true, required = true)
        sheet.checklist("roles", getString(R.string.photo_roles), roles.map { it.str("name") to it.str("label").ifBlank { it.str("name") } },
            a?.optJSONArray("roles").strings().toSet())
            .show { v ->
                api.postJson("/api/admin/photo-access/users", JSONObject().put("hakbun", a?.str("hakbun") ?: v.text("hakbun")).put("roles", jsonArray(v.list("roles"))))
                reload()
            }
    }

    override fun onSwipe(row: Row) {
        val (kind, id) = row.id.split(":", limit = 2).let { it[0] to it[1] }
        if (kind == "role" && id == "add") return
        Dialogs.confirmSensitive(requireContext(), getString(R.string.delete), getString(R.string.role_delete_message, row.title), keyword = id) {
            act(getString(R.string.deleted)) {
                if (kind == "role") api.deleteJson("/api/admin/photo-access/roles/${android.net.Uri.encode(id)}")
                else api.deleteJson("/api/admin/photo-access/users/$id")
            }
        }
    }
}
