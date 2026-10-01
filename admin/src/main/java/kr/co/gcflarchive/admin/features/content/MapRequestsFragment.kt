package kr.co.gcflarchive.admin.features.content

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.flat
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject

/** 지도 요청 처리: 신규 장소 요청 / 영업시간 제안 승인·반려. */
class MapRequestsFragment : ListFragment() {
    override val emptyText get() = getString(R.string.map_no_pending)

    override suspend fun load(): List<Row> {
        val json = fetch("/api/admin/map/pending-items").json()
        val places = json.optJSONArray("pendingPlaces").objects()
        val details = json.optJSONArray("pendingDetails").objects()
        setSummary(getString(R.string.map_summary, places.size, details.size))
        val rows = mutableListOf<Row>()
        if (places.isNotEmpty()) rows += Row.header(getString(R.string.map_new_places))
        rows += places.map { p ->
            Row("place:" + p.str("placeId"), p.str("placeName"), joinMeta(p.str("roadAddress"), p.str("floor"), p.str("otherInfo")),
                joinMeta(p.str("submittedBy"), shortTime(p.str("timestamp"))), badges = listOf("신규 장소" to Badge.INFO), trailing = Trailing.Chevron, payload = p)
        }
        if (details.isNotEmpty()) rows += Row.header(getString(R.string.map_hours))
        rows += details.map { d ->
            Row("detail:" + d.str("requestId"), d.str("placeName"), scheduleText(d.optJSONObject("schedule")),
                joinMeta(d.str("submittedBy"), shortTime(d.str("timestamp"))), badges = listOf("영업시간" to Badge.PURPLE), trailing = Trailing.Chevron, payload = d)
        }
        return rows
    }

    private fun scheduleText(s: JSONObject?): String? = s?.flat()?.entries?.joinToString("\n") { (day, v) -> "$day: $v" }

    override fun onRowClick(row: Row) {
        if (!guardWrite()) return
        val o = row.payload as? JSONObject ?: return
        val (kind, id) = row.id.split(":", limit = 2).let { it[0] to it[1] }
        val path = if (kind == "place") "/api/admin/map/approve-custom-place/$id" else "/api/admin/map/approve-place-detail/$id"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(row.title)
            .setMessage(o.flat().entries.joinToString("\n") { (k, v) -> "$k: $v" })
            .setNegativeButton(R.string.map_reject) { _, _ -> act(getString(R.string.map_rejected)) { api.postJson(path, JSONObject().put("action", "reject")) } }
            .setPositiveButton(R.string.map_approve) { _, _ -> act(getString(R.string.map_approved)) { api.postJson(path, JSONObject().put("action", "approve")) } }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }
}
