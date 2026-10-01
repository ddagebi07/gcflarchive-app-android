package kr.co.gcflarchive.admin.features.content

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.Permission
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.features.strings
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject

/**
 * 자료: PDF / 영상 / 호스팅 파일 탭, 검색, 항목 상세(공개·인증 필수 토글, 메타데이터 수정),
 * 다중 선택 삭제(상단 바), 간편 업로드(FAB).
 */
class DocumentsAdminFragment : ListFragment() {
    private enum class Kind(val label: String, val archiveType: String) { PDF("PDF", "pdf"), VIDEO("영상", "video"), FILE("호스팅 파일", "file") }

    private var kind = Kind.PDF
    override val searchHint get() = getString(R.string.doc_search_hint)
    override val fab get() = getString(R.string.upload_quick) to R.drawable.ic_upload
    override val selectionAction get() = getString(R.string.delete)

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { Upload.pdfForm(requireActivity(), it) { reload() } }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val kinds = Kind.entries.filter { if (it == Kind.VIDEO) grants.has(Permission.VIDEOS) else grants.has(Permission.PDFS) }
        if (kind !in kinds) kind = kinds.firstOrNull() ?: Kind.PDF
        setChips(kinds.map { it.name to it.label }, kind.name) {
            kind = Kind.valueOf(it)
            endSelection()
            reload()
        }
    }

    override suspend fun load(): List<Row> {
        val rows = when (kind) {
            Kind.PDF -> fetch("/api/documents").json().optJSONArray("documents").objects()
                .sortedByDescending { it.str("modified") }
                .map { o ->
                    val private = o.str("visibility") == "private"
                    Row(
                        id = o.str("filename"),
                        title = o.str("displayName").ifBlank { o.str("filename") },
                        subtitle = joinMeta(o.str("category"), o.str("collectionName")),
                        meta = joinMeta(shortTime(o.str("modified")), "조회 ${o.optInt("viewCount")}", "다운 ${o.optInt("downloadCount")}"),
                        badges = listOfNotNull(
                            if (private) "비공개" to Badge.DANGER else "공개" to Badge.SUCCESS,
                            if (o.optBoolean("requireVerification")) "인증 필수" to Badge.INFO else null,
                            if (o.optBoolean("hasAnswerSheet")) "답지" to Badge.PURPLE else null,
                        ),
                        trailing = Trailing.Chevron, selectable = true, payload = o,
                    )
                }
            Kind.VIDEO -> fetch("/api/videos").json().optJSONArray("videos").objects().map { o ->
                Row(
                    id = o.str("youtubeUrl"),
                    title = o.str("collectionName").ifBlank { o.str("youtubeUrl") },
                    subtitle = joinMeta(o.str("category"), o.optJSONArray("tags").strings().joinToString(" ") { "#$it" }),
                    meta = joinMeta(o.str("youtubeUrl"), shortTime(o.str("timestamp"))),
                    trailing = Trailing.Chevron, selectable = true, payload = o,
                )
            }
            Kind.FILE -> fetch("/api/admin/hosted-files").json().optJSONArray("hosted").objects().map { o ->
                Row(
                    id = o.str("stored"),
                    title = o.str("original").ifBlank { o.str("stored") },
                    subtitle = joinMeta(o.str("category"), o.str("collectionName")),
                    meta = joinMeta(o.str("url"), shortTime(o.str("timestamp"))),
                    trailing = Trailing.Chevron, selectable = true, payload = o,
                )
            }
        }
        setSummary(getString(R.string.doc_summary, rows.size))
        return rows
    }

    override fun onRowClick(row: Row) {
        if (!guardWrite()) return
        val o = row.payload as? JSONObject ?: return
        val sheet = FormSheet(requireActivity(), getString(R.string.doc_edit), row.id)
        if (kind == Kind.PDF) {
            sheet.switch("public", getString(R.string.doc_public), o.str("visibility") != "private")
                .switch("requireVerification", getString(R.string.doc_require_verification), o.optBoolean("requireVerification"))
                .text("displayName", getString(R.string.doc_title), o.str("displayName"))
        }
        sheet.text("category", getString(R.string.doc_category), o.str("category"))
            .text("collectionName", getString(R.string.doc_collection), o.str("collectionName"))
            .text("tags", getString(R.string.doc_tags), o.optJSONArray("tags").strings().joinToString(", "), hint = getString(R.string.doc_tags_hint))
            .text("notes", getString(R.string.memo), o.str("notes"), multiline = true)
            .show { v ->
                // /api/update overwrites every metadata field, so always send the full set.
                val body = JSONObject()
                    .put("archiveType", kind.archiveType)
                    .put("target", row.id)
                    .put("category", v.text("category"))
                    .put("collectionName", v.text("collectionName"))
                    .put("tags", v.text("tags"))
                    .put("notes", v.text("notes"))
                if (kind == Kind.PDF) {
                    body.put("displayName", v.text("displayName"))
                        .put("visibility", if (v.bool("public")) "public" else "private")
                        .put("requireVerification", v.bool("requireVerification"))
                }
                api.postJson("/api/update", body)
                reload()
            }
    }

    override fun onSelectionAction(rows: List<Row>) {
        Dialogs.confirmSensitive(
            requireContext(), getString(R.string.doc_delete_title, rows.size),
            getString(R.string.doc_delete_message, rows.joinToString("\n") { "· ${it.title}" }),
            keyword = getString(R.string.delete),
        ) {
            endSelection()
            act(getString(R.string.deleted)) {
                api.postForm("/api/delete", mapOf("archiveType" to kind.archiveType, "target" to rows.joinToString(",") { it.id }))
            }
        }
    }

    override fun onFab() {
        val choices = buildList {
            if (grants.has(Permission.PDFS)) add(getString(R.string.upload_pdf_title))
            if (grants.has(Permission.VIDEOS)) add(getString(R.string.upload_video_title))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.upload_quick)
            .setItems(choices.toTypedArray()) { _, i ->
                if (choices[i] == getString(R.string.upload_pdf_title)) pickPdf.launch(arrayOf("application/pdf"))
                else Upload.youtubeForm(requireActivity()) { reload() }
            }
            .show()
    }
}
