package kr.co.gcflarchive.admin.features.community

import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject

/** 신고 검토: 숨겨진 글·댓글이 먼저 오고, 눌러서 다시 보이기·숨기기·삭제를 골라요. */
class CommunityReportsFragment : ListFragment() {
    override val searchHint get() = getString(R.string.community_reports_search)
    override val emptyText get() = getString(R.string.community_reports_empty)

    override suspend fun load(): List<Row> {
        val items = fetch("/api/community/admin/reports").json().optJSONArray("items").objects()
        val blinded = items.count { it.str("status") == "blinded" }
        setSummary(getString(R.string.community_reports_summary, items.size, blinded))
        return items.map { o ->
            val isPost = o.str("targetType") == "post"
            Row(
                id = "${o.str("targetType")}:${o.optInt("targetId")}",
                title = (if (isPost) o.str("title") else o.str("content")).ifBlank { getString(R.string.community_no_title) },
                subtitle = if (isPost) o.str("content").replace('\n', ' ').take(80) else getString(R.string.community_comment_on, o.str("title")),
                meta = joinMeta(o.str("authorName"), CommunityAdmin.time(o.str("createdAt")), getString(R.string.community_report_count, o.optInt("reportCount"))),
                badges = listOf(
                    (if (isPost) getString(R.string.community_kind_post) else getString(R.string.community_kind_comment)) to Badge.NEUTRAL,
                    if (o.str("status") == "blinded") getString(R.string.community_status_blinded) to Badge.DANGER
                    else getString(R.string.community_status_visible) to Badge.INFO,
                ),
                trailing = Trailing.Chevron,
                payload = o,
            )
        }
    }

    override fun onRowClick(row: Row) {
        val o = row.payload as? JSONObject ?: return
        if (!guardWrite()) return
        val reasons = o.optJSONArray("reasons").objects()
            .joinToString("\n") { "· ${it.str("reason").ifBlank { getString(R.string.community_reason_none) }} (${CommunityAdmin.time(it.str("createdAt"))})" }
            .ifBlank { getString(R.string.community_reason_manual) }
        val blinded = o.str("status") == "blinded"
        val options = buildList {
            add("restore" to getString(R.string.community_action_restore))
            if (!blinded) add("blind" to getString(R.string.community_action_blind))
            add("delete" to getString(R.string.community_action_delete))
        }
        FormSheet(requireActivity(), row.title.toString(), getString(R.string.community_review_desc, o.str("authorName"), o.str("authorKey")))
            .note(o.str("content").ifBlank { "-" })
            .note(getString(R.string.community_reasons, reasons))
            .select("action", getString(R.string.community_action), options, if (blinded) "restore" else "blind")
            .text("note", getString(R.string.community_moderate_note), hint = getString(R.string.community_moderate_note_hint))
            .submitText(getString(R.string.community_moderate_submit))
            .show { v ->
                val body = JSONObject()
                    .put("targetType", o.str("targetType"))
                    .put("targetId", o.optInt("targetId"))
                    .put("action", v.text("action"))
                    .put("note", v.text("note"))
                api.postJson("/api/community/admin/moderate", body)
                reload()
            }
    }
}
