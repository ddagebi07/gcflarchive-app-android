package kr.co.gcflarchive.admin.features.more

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject

/** 기출 과목 그룹·학과 편집, 면책조항 PDF 사용 여부·업로드. */
class ExamSettingsFragment : ListFragment() {
    private var groups: MutableList<Pair<String, List<String>>> = mutableListOf()
    private var departments: List<String> = emptyList()

    private val pickDisclaimer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::uploadDisclaimer) }

    override suspend fun load(): List<Row> {
        val subj = fetch("/api/past-exam-subjects").json()
        groups = subj.optJSONArray("groups").objects().map { it.str("label") to it.optJSONArray("subjects").strings() }.toMutableList()
        departments = subj.optJSONArray("departments").strings().ifEmpty { listOf("공통", "영어과", "비영어과") }
        val disclaimer = runCatching { fetch("/api/admin/disclaimer-config").json() }.getOrNull()

        val rows = mutableListOf(Row.header(getString(R.string.exam_subject_groups)))
        rows += groups.mapIndexed { i, (label, subjects) ->
            Row("group:$i", label, subjects.joinToString(", "), trailing = Trailing.Chevron, swipeable = true)
        }
        rows += Row("group:add", getString(R.string.exam_group_add), icon = R.drawable.ic_add, trailing = Trailing.Chevron)
        rows += Row.header(getString(R.string.exam_departments))
        rows += Row("departments", departments.joinToString(" · "), getString(R.string.exam_departments_desc), trailing = Trailing.Chevron)
        disclaimer?.let { d ->
            rows += Row.header(getString(R.string.exam_disclaimer))
            rows += Row("disclaimer_enabled", getString(R.string.exam_disclaimer_enabled), getString(R.string.exam_disclaimer_desc),
                trailing = Trailing.Switch(d.optBoolean("enabled"), enabled = d.optBoolean("hasDisclaimer")))
            rows += Row("disclaimer_upload", getString(R.string.exam_disclaimer_upload), d.str("filename").ifBlank { getString(R.string.exam_disclaimer_none) },
                badges = listOf((if (d.optBoolean("hasDisclaimer")) "등록됨" else "없음") to (if (d.optBoolean("hasDisclaimer")) Badge.SUCCESS else Badge.NEUTRAL)),
                icon = R.drawable.ic_upload, trailing = Trailing.Chevron)
        }
        return rows
    }

    private fun saveSubjects() = act(getString(R.string.saved)) {
        api.putJson(
            "/api/admin/past-exam-subjects",
            JSONObject()
                .put("groups", JSONArray(groups.map { (label, subjects) -> JSONObject().put("label", label).put("subjects", jsonArray(subjects)) }))
                .put("departments", jsonArray(departments)),
        )
    }

    override fun onRowClick(row: Row) {
        if (!guardWrite()) return
        when {
            row.id == "group:add" -> editGroup(null)
            row.id.startsWith("group:") -> editGroup(row.id.removePrefix("group:").toInt())
            row.id == "departments" -> FormSheet(requireActivity(), getString(R.string.exam_departments))
                .chips("departments", getString(R.string.exam_departments), departments, getString(R.string.chip_add_hint))
                .show { v ->
                    departments = v.list("departments")
                    saveSubjects()
                }
            row.id == "disclaimer_upload" -> pickDisclaimer.launch(arrayOf("application/pdf"))
        }
    }

    private fun editGroup(index: Int?) {
        val current = index?.let { groups[it] }
        FormSheet(requireActivity(), getString(if (index == null) R.string.exam_group_add else R.string.exam_group_edit))
            .text("label", getString(R.string.exam_group_label), current?.first.orEmpty(), required = true)
            .chips("subjects", getString(R.string.exam_group_subjects), current?.second.orEmpty(), getString(R.string.chip_add_hint))
            .show { v ->
                val entry = v.text("label") to v.list("subjects")
                if (index == null) groups.add(entry) else groups[index] = entry
                saveSubjects()
            }
    }

    override fun onRowSwitch(row: Row, checked: Boolean) {
        if (row.id == "disclaimer_enabled") act(getString(R.string.saved)) { api.postJson("/api/admin/disclaimer-config", JSONObject().put("enabled", checked)) }
    }

    override fun onSwipe(row: Row) {
        if (!row.id.startsWith("group:") || row.id == "group:add") return
        val i = row.id.removePrefix("group:").toInt()
        Dialogs.confirm(requireContext(), getString(R.string.delete), getString(R.string.exam_group_delete, row.title), getString(R.string.delete)) {
            groups.removeAt(i)
            saveSubjects()
        }
    }

    private fun uploadDisclaimer(uri: Uri) {
        val ctx = requireContext()
        act(getString(R.string.saved)) {
            val body = object : RequestBody() {
                override fun contentType() = "application/pdf".toMediaType()
                override fun writeTo(sink: BufferedSink) {
                    ctx.contentResolver.openInputStream(uri)?.source()?.use { sink.writeAll(it) }
                }
            }
            api.postMultipart("/api/admin/upload-disclaimer", emptyMap(), listOf(Triple("file", "disclaimer.pdf", body)))
        }
    }
}
