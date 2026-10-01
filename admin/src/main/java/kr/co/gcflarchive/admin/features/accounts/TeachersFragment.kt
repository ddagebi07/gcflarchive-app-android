package kr.co.gcflarchive.admin.features.accounts

import android.net.Uri
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject

/** 교사 프로필: 별칭·연락처·메모 수정, 삭제. */
class TeachersFragment : ListFragment() {
    override val searchHint get() = getString(R.string.teacher_search_hint)
    override val fab get() = getString(R.string.teacher_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.delete)

    override suspend fun load(): List<Row> {
        val list = fetch("/api/teacher-profiles").json().optJSONArray("profiles").objects()
        setSummary(getString(R.string.teacher_summary, list.size))
        return list.map { o ->
            Row(
                id = o.str("teacherId"),
                title = listOf(o.str("name"), o.str("alias")).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { o.str("teacherId") },
                subtitle = joinMeta(o.str("teacherId"), o.str("email"), o.str("contacts")),
                meta = o.str("notes").ifBlank { null },
                badges = listOf((if (o.str("consentAcceptedAt").isNotBlank()) "동의 완료" else "동의 전") to (if (o.str("consentAcceptedAt").isNotBlank()) Badge.SUCCESS else Badge.NEUTRAL)),
                trailing = Trailing.Chevron,
                swipeable = true,
                payload = o,
            )
        }
    }

    override fun onFab() = edit(null)

    override fun onRowClick(row: Row) {
        if (guardWrite()) edit(row.payload as JSONObject)
    }

    private fun edit(o: JSONObject?) {
        val sheet = FormSheet(requireActivity(), getString(if (o == null) R.string.teacher_add else R.string.teacher_edit),
            o?.let { joinMeta(it.str("name"), it.str("email"), shortTime(it.str("consentAcceptedAt"))) })
        if (o == null) sheet.text("teacherId", getString(R.string.teacher_id), required = true)
        sheet.text("alias", getString(R.string.teacher_alias), o?.str("alias").orEmpty())
            .text("contacts", getString(R.string.teacher_contacts), o?.str("contacts").orEmpty())
            .text("notes", getString(R.string.memo), o?.str("notes").orEmpty(), multiline = true)
            .show { v ->
                api.postJson(
                    "/api/teacher-profiles",
                    JSONObject().put("teacherId", o?.str("teacherId") ?: v.text("teacherId"))
                        .put("alias", v.text("alias")).put("contacts", v.text("contacts")).put("notes", v.text("notes")),
                )
                reload()
            }
    }

    override fun onSwipe(row: Row) {
        Dialogs.confirmSensitive(requireContext(), getString(R.string.teacher_delete), getString(R.string.teacher_delete_message, row.id), keyword = row.id) {
            act(getString(R.string.deleted)) { api.deleteJson("/api/teacher-profiles/${Uri.encode(row.id)}") }
        }
    }
}
