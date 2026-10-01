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
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject

/** 사진첩: 앨범 목록·수정 (제목·URL·일자·공개 역할). */
class PhotoAlbumsFragment : ListFragment() {
    override val searchHint get() = getString(R.string.photo_search_hint)
    override val fab get() = getString(R.string.photo_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.delete)
    private var roles: List<String> = emptyList()

    override suspend fun load(): List<Row> {
        roles = runCatching { fetch("/api/admin/photo-access").json().optJSONArray("roles").objects().map { it.str("name") } }.getOrDefault(emptyList())
        val albums = fetch("/api/admin/photo-albums").json().optJSONArray("albums").objects().sortedByDescending { it.str("albumDate") }
        setSummary(getString(R.string.photo_summary, albums.size))
        return albums.map { o ->
            val req = o.optJSONArray("requiredRoles").strings()
            Row(
                id = o.str("id"),
                title = o.str("title").ifBlank { o.str("collectionName") },
                subtitle = o.str("albumUrl"),
                meta = joinMeta(o.str("albumDate"), o.str("copyright"), o.optJSONArray("performers").strings().joinToString(", ")),
                badges = if (req.isEmpty()) listOf("전체 공개" to Badge.SUCCESS) else req.map { it to Badge.INFO },
                trailing = Trailing.Chevron, swipeable = true, payload = o,
            )
        }
    }

    override fun onFab() = edit(null)

    override fun onRowClick(row: Row) {
        if (guardWrite()) edit(row.payload as JSONObject)
    }

    private fun edit(o: JSONObject?) {
        FormSheet(requireActivity(), getString(if (o == null) R.string.photo_add else R.string.photo_edit))
            .text("title", getString(R.string.photo_title), o?.str("title").orEmpty(), required = true)
            .text("albumUrl", getString(R.string.photo_url), o?.str("albumUrl").orEmpty(), required = true,
                validate = { if (it.startsWith("http")) null else getString(R.string.url_invalid) })
            .text("albumDate", getString(R.string.photo_date), o?.str("albumDate").orEmpty(), hint = "YYYY-MM-DD")
            .text("collectionName", getString(R.string.doc_collection), o?.str("collectionName").orEmpty())
            .text("copyright", getString(R.string.photo_copyright), o?.str("copyright").orEmpty())
            .text("thumbnailUrl", getString(R.string.photo_thumbnail), o?.str("thumbnailUrl").orEmpty())
            .chips("performers", getString(R.string.photo_performers), o?.optJSONArray("performers").strings(), getString(R.string.chip_add_hint))
            .checklist("requiredRoles", getString(R.string.photo_roles), roles.map { it to it }, o?.optJSONArray("requiredRoles").strings().orEmpty().toSet())
            .note(getString(R.string.photo_roles_note))
            .text("notes", getString(R.string.memo), o?.str("notes").orEmpty(), multiline = true)
            .show { v ->
                val body = JSONObject()
                    .put("title", v.text("title")).put("albumUrl", v.text("albumUrl")).put("albumDate", v.text("albumDate"))
                    .put("collectionName", v.text("collectionName")).put("copyright", v.text("copyright"))
                    .put("thumbnailUrl", v.text("thumbnailUrl")).put("notes", v.text("notes"))
                    .put("performers", jsonArray(v.list("performers")))
                    .put("requiredRoles", jsonArray(v.list("requiredRoles")))
                o?.let { body.put("id", it.str("id")) }
                api.postJson("/api/admin/photo-albums", body)
                reload()
            }
    }

    override fun onSwipe(row: Row) {
        Dialogs.confirmSensitive(requireContext(), getString(R.string.photo_delete), getString(R.string.photo_delete_message, row.title), keyword = getString(R.string.delete)) {
            act(getString(R.string.deleted)) { api.deleteJson("/api/admin/photo-albums/${android.net.Uri.encode(row.id)}") }
        }
    }
}
