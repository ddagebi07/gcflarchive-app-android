package kr.co.gcflarchive.admin.features.community

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
 * 설문조사 관리: 누가 설문을 만들 수 있는지 (커뮤니티 범위·학번 규칙 역할) 정하고,
 * 모든 설문을 보고 마감·다시 열기·삭제해요. 설문은 학생 앱에서 만들고 답해요.
 */
class SurveysFragment : ListFragment() {
    override val swipeLabel get() = getString(R.string.delete)
    override val emptyText get() = getString(R.string.survey_admin_empty)

    private var settings: JSONObject? = null

    override suspend fun load(): List<Row> {
        val s = fetch("/api/surveys/admin/settings").json()
        settings = s
        val surveys = fetch("/api/surveys?all=1").json().optJSONArray("mine").objects()
        setSummary(getString(R.string.survey_admin_summary, surveys.size))
        val creators = Row(
            id = CREATORS_ROW,
            title = getString(R.string.survey_admin_creators),
            subtitle = s.str("creatorLabel"),
            icon = R.drawable.ic_person,
            trailing = Trailing.Chevron,
        )
        return listOf(creators) + surveys.map { v ->
            Row(
                id = v.optInt("id").toString(),
                title = v.str("title"),
                subtitle = listOfNotNull(
                    v.str("ownerName").ifBlank { null },
                    getString(R.string.survey_admin_responses, v.optInt("responseCount")),
                    v.str("audienceLabel").ifBlank { null },
                ).joinToString(" · "),
                badges = listOfNotNull(
                    v.str("statusLabel") to when (v.str("status")) { "open" -> Badge.INFO; "draft" -> Badge.NEUTRAL; else -> Badge.NEUTRAL },
                    if (v.optBoolean("collectIdentity")) getString(R.string.survey_admin_identity) to Badge.DANGER else null,
                ),
                trailing = Trailing.Chevron,
                swipeable = true,
                payload = v,
            )
        }
    }

    override fun onRowClick(row: Row) {
        if (!guardWrite()) return
        if (row.id == CREATORS_ROW) return editCreators()
        val v = row.payload as? JSONObject ?: return
        val open = v.str("status") == "open"
        Dialogs.confirm(
            requireContext(),
            v.str("title"),
            getString(if (open) R.string.survey_admin_close_desc else R.string.survey_admin_open_desc),
            getString(if (open) R.string.survey_admin_close else R.string.survey_admin_open),
        ) {
            act(getString(R.string.saved)) {
                api.putJson("/api/surveys/${v.optInt("id")}", JSONObject().put("status", if (open) "closed" else "open"))
            }
        }
    }

    private fun editCreators() {
        val s = settings ?: return
        val options = s.optJSONArray("scopes").objects().map { it.str("value") to it.str("label") }
        FormSheet(requireActivity(), getString(R.string.survey_admin_creators), getString(R.string.survey_admin_creators_desc))
            .checklist("scope", getString(R.string.survey_admin_creators), options, s.optJSONArray("creatorScope").strings().toSet())
            .submitText(getString(R.string.save))
            .show { values ->
                api.putJson("/api/surveys/admin/settings", JSONObject().put("creatorScope", jsonArray(values.list("scope"))))
                reload()
            }
    }

    override fun onSwipe(row: Row) {
        val v = row.payload as? JSONObject ?: return reload()
        Dialogs.confirmSensitive(
            requireContext(),
            getString(R.string.survey_admin_delete_title, row.title),
            getString(R.string.survey_admin_delete_desc, v.optInt("responseCount")),
            keyword = getString(R.string.delete),
        ) {
            act(getString(R.string.deleted)) { api.deleteJson("/api/surveys/${v.optInt("id")}") }
        }
    }

    private companion object {
        const val CREATORS_ROW = "__creators"
    }
}
