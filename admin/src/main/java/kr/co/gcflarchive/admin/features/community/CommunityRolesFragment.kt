package kr.co.gcflarchive.admin.features.community

import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

/**
 * 역할: 학번 규칙(학년·반·번호·패턴·계정 종류·포함/제외 학번)으로 묶은 사람들.
 * 게시판의 읽기·글쓰기·댓글 권한에 넣어요. 맨 위 줄에서 학번 하나로 미리 확인할 수 있어요.
 */
class CommunityRolesFragment : ListFragment() {
    override val fab get() = getString(R.string.community_role_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.delete)
    override val emptyText get() = getString(R.string.community_roles_empty)

    private var types: List<Pair<String, String>> = listOf("student" to "학생", "teacher" to "선생님", "custom" to "특수 계정")

    override suspend fun load(): List<Row> {
        val data = fetch("/api/community/admin/roles").json()
        data.optJSONArray("types").objects().map { it.str("value") to it.str("label") }.takeIf { it.isNotEmpty() }?.let { types = it }
        val roles = data.optJSONArray("roles").objects()
        setSummary(getString(R.string.community_roles_summary, roles.size))
        val check = Row(
            id = CHECK_ROW,
            title = getString(R.string.community_role_check),
            subtitle = getString(R.string.community_role_check_desc),
            icon = R.drawable.ic_check,
            trailing = Trailing.Chevron,
        )
        return listOf(check) + roles.map { r ->
            val used = r.optJSONArray("usedBy").strings()
            Row(
                id = r.str("id"),
                title = r.str("name"),
                subtitle = r.str("summary"),
                meta = if (used.isEmpty()) getString(R.string.community_role_unused)
                else getString(R.string.community_role_used_by, used.joinToString(", ")),
                badges = listOf(r.str("id") to Badge.NEUTRAL),
                trailing = Trailing.Chevron,
                swipeable = true,
                payload = r,
            )
        }
    }

    override fun onFab() = edit(null)

    override fun onRowClick(row: Row) {
        if (row.id == CHECK_ROW) check()
        else if (guardWrite()) edit(row.payload as? JSONObject)
    }

    private fun edit(role: JSONObject?) {
        val rule = role?.optJSONObject("rule") ?: JSONObject()
        val sheet = FormSheet(
            requireActivity(),
            getString(if (role == null) R.string.community_role_add else R.string.community_role_edit),
            getString(R.string.community_role_form_desc),
        )
        if (role == null) {
            sheet.text("id", getString(R.string.community_role_id), hint = getString(R.string.community_role_id_hint), required = true,
                validate = { CommunityAdmin.boardIdError(it.lowercase()) })
        }
        sheet.text("name", getString(R.string.community_role_name), role?.str("name").orEmpty(),
            hint = getString(R.string.community_role_name_hint), required = true)
            .text("description", getString(R.string.community_role_desc), role?.str("description").orEmpty())
            .checklist("grades", getString(R.string.community_role_grades), GRADES, rule.optJSONArray("grades").strings().toSet())
            .text("classes", getString(R.string.community_role_classes), rule.str("classes"),
                hint = getString(R.string.community_role_classes_hint), validate = { CommunityAdmin.rangeError(it, "반") })
            .text("numbers", getString(R.string.community_role_numbers), rule.str("numbers"),
                hint = getString(R.string.community_role_numbers_hint), validate = { CommunityAdmin.rangeError(it, "번호") })
            .checklist("types", getString(R.string.community_role_types), types, rule.optJSONArray("types").strings().toSet())
            .chips("patterns", getString(R.string.community_role_patterns), rule.optJSONArray("patterns").strings(),
                getString(R.string.community_role_patterns_hint))
            .chips("include", getString(R.string.community_role_include), rule.optJSONArray("include").strings(),
                getString(R.string.community_role_hakbun_hint))
            .chips("exclude", getString(R.string.community_role_exclude), rule.optJSONArray("exclude").strings(),
                getString(R.string.community_role_hakbun_hint))
            .note(getString(R.string.community_role_rule_note))
            .submitText(getString(if (role == null) R.string.community_board_create else R.string.save))
            .show { v ->
                val patterns = v.list("patterns")
                val include = v.list("include")
                val exclude = v.list("exclude")
                (CommunityAdmin.patternError(patterns) ?: CommunityAdmin.hakbunListError(include + exclude))
                    ?.let { throw IllegalArgumentException(it) }
                val body = JSONObject()
                    .put("name", v.text("name"))
                    .put("description", v.text("description"))
                    .put("rule", JSONObject()
                        .put("grades", jsonArray(v.list("grades")))
                        .put("classes", v.text("classes"))
                        .put("numbers", v.text("numbers"))
                        .put("types", jsonArray(v.list("types")))
                        .put("patterns", jsonArray(patterns))
                        .put("include", jsonArray(include))
                        .put("exclude", jsonArray(exclude)))
                if (role == null) {
                    api.postJson("/api/community/admin/roles", body.put("id", v.text("id").lowercase()))
                } else {
                    api.putJson("/api/community/admin/roles/${role.str("id")}", body)
                }
                reload()
            }
    }

    /** 학번(또는 교사 ID) 하나가 어떤 역할에 들고, 게시판마다 무엇을 할 수 있는지 보여줘요. */
    private fun check() {
        FormSheet(requireActivity(), getString(R.string.community_role_check), getString(R.string.community_role_check_form_desc))
            .text("userId", getString(R.string.community_role_check_id), required = true)
            .submitText(getString(R.string.community_role_check_submit))
            .show { v ->
                val res = api.postJson("/api/community/admin/roles/check", JSONObject().put("userId", v.text("userId")))
                val roles = res.optJSONArray("roles").strings()
                val lines = res.optJSONArray("boards").objects().map { b ->
                    val can = listOfNotNull(
                        getString(R.string.community_role_can_read).takeIf { b.optBoolean("canRead") },
                        getString(R.string.community_role_can_write).takeIf { b.optBoolean("canWrite") },
                        getString(R.string.community_role_can_comment).takeIf { b.optBoolean("canComment") },
                    )
                    "${b.str("name")}: " + if (can.isEmpty()) getString(R.string.community_role_can_none) else can.joinToString("·")
                }
                val message = getString(
                    R.string.community_role_check_result,
                    if (roles.isEmpty()) getString(R.string.community_role_check_no_roles) else roles.joinToString(", "),
                ) + "\n\n" + lines.joinToString("\n")
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(res.str("userId"))
                    .setMessage(message)
                    .setPositiveButton(R.string.cancel, null)
                    .show()
            }
    }

    override fun onSwipe(row: Row) {
        val r = row.payload as? JSONObject ?: return
        Dialogs.confirm(
            requireContext(),
            getString(R.string.community_role_delete_title, row.title),
            getString(R.string.community_role_delete_desc),
            getString(R.string.delete),
        ) {
            act(getString(R.string.deleted)) { api.deleteJson("/api/community/admin/roles/${r.str("id")}") }
        }
    }

    private companion object {
        const val CHECK_ROW = "__check"
        val GRADES = listOf("1" to "1학년", "2" to "2학년", "3" to "3학년")
    }
}
