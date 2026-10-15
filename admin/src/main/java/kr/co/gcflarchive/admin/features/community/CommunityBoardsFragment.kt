package kr.co.gcflarchive.admin.features.community

import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.features.jsonArray
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import org.json.JSONObject

/**
 * 게시판: 추가·이름·설명·읽기/쓰기/댓글 권한·순서·익명 허용·운영 여부를 수정하고, 밀어서 삭제해요.
 * 권한은 기본 범위와 역할(학번 규칙)을 여러 개 고를 수 있어요.
 */
class CommunityBoardsFragment : ListFragment() {
    /** value to label: basic scopes, then roles (from the server). */
    private var scopes: List<Pair<String, String>> = CommunityAdmin.SCOPES

    override val fab get() = getString(R.string.community_board_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.delete)
    override val emptyText get() = getString(R.string.community_boards_empty)

    override suspend fun load(): List<Row> {
        val data = fetch("/api/community/admin/boards").json()
        data.optJSONArray("scopes").objects().map { it.str("value") to it.str("label") }.takeIf { it.isNotEmpty() }?.let { scopes = it }
        val boards = data.optJSONArray("boards").objects()
        setSummary(getString(R.string.community_boards_summary, boards.size))
        return boards.map { b ->
            Row(
                id = b.str("id"),
                title = b.str("name"),
                subtitle = b.str("description").ifBlank { null },
                meta = getString(
                    R.string.community_board_meta,
                    b.str("readLabel").ifBlank { CommunityAdmin.scopeLabel(b.str("readScope"), scopes) },
                    b.str("writeLabel").ifBlank { CommunityAdmin.scopeLabel(b.str("writeScope"), scopes) },
                    b.str("commentLabel").ifBlank { CommunityAdmin.scopeLabel(b.str("commentScope").ifBlank { b.str("writeScope") }, scopes) },
                    b.optInt("postCount"),
                ),
                badges = listOfNotNull(
                    b.str("id") to Badge.NEUTRAL,
                    if (!b.optBoolean("isActive", true)) getString(R.string.community_board_paused) to Badge.DANGER else null,
                    if (b.optBoolean("allowAnonymous")) getString(R.string.community_board_anonymous) to Badge.INFO else null,
                ),
                trailing = Trailing.Chevron,
                swipeable = true,
                payload = b,
            )
        }
    }

    override fun onFab() = edit(null)

    override fun onRowClick(row: Row) {
        if (guardWrite()) edit(row.payload as? JSONObject)
    }

    private fun edit(board: JSONObject?) {
        val sheet = FormSheet(
            requireActivity(),
            getString(if (board == null) R.string.community_board_add else R.string.community_board_edit),
            if (board == null) getString(R.string.community_board_add_desc) else null,
        )
        if (board == null) {
            sheet.text("id", getString(R.string.community_board_id), hint = getString(R.string.community_board_id_hint), required = true,
                validate = { CommunityAdmin.boardIdError(it.lowercase()) })
        }
        sheet.text("name", getString(R.string.community_board_name), board?.str("name").orEmpty(), required = true)
            .text("description", getString(R.string.community_board_desc), board?.str("description").orEmpty(), multiline = true)
            .checklist("readScope", getString(R.string.community_board_read), scopes,
                CommunityAdmin.splitScope(board?.str("readScope") ?: "login").toSet())
            .checklist("writeScope", getString(R.string.community_board_write), scopes,
                CommunityAdmin.splitScope(board?.str("writeScope") ?: "login").toSet())
            .checklist("commentScope", getString(R.string.community_board_comment), scopes,
                CommunityAdmin.splitScope(board?.str("commentScope").orEmpty()).toSet())
            .note(getString(R.string.community_board_scope_note))
            .stepper("sortOrder", getString(R.string.community_board_order), board?.optInt("sortOrder") ?: 0, 0, 99)
            .switch("allowAnonymous", getString(R.string.community_board_allow_anonymous), board?.optBoolean("allowAnonymous") ?: false,
                getString(R.string.community_board_allow_anonymous_desc))
            .switch("isActive", getString(R.string.community_board_active), board?.optBoolean("isActive", true) ?: true,
                getString(R.string.community_board_active_desc))
            .submitText(getString(if (board == null) R.string.community_board_create else R.string.save))
            .show { v ->
                val body = JSONObject()
                    .put("name", v.text("name"))
                    .put("description", v.text("description"))
                    .put("readScope", jsonArray(v.list("readScope")))
                    .put("writeScope", jsonArray(v.list("writeScope")))
                    .put("commentScope", jsonArray(v.list("commentScope")))
                    .put("sortOrder", v.int("sortOrder"))
                    .put("allowAnonymous", v.bool("allowAnonymous"))
                    .put("isActive", v.bool("isActive"))
                if (board == null) {
                    api.postJson("/api/community/admin/boards", body.put("id", v.text("id").lowercase()))
                } else {
                    api.putJson("/api/community/admin/boards/${board.str("id")}", body)
                }
                reload()
            }
    }

    override fun onSwipe(row: Row) {
        val b = row.payload as? JSONObject ?: return
        Dialogs.confirmSensitive(
            requireContext(),
            getString(R.string.community_board_delete_title, row.title),
            getString(R.string.community_board_delete_desc, b.optInt("postCount")),
            keyword = b.str("id"),
        ) {
            act(getString(R.string.deleted)) { api.deleteJson("/api/community/admin/boards/${b.str("id")}") }
        }
    }
}
