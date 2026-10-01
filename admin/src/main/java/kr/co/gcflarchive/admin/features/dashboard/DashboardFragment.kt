package kr.co.gcflarchive.admin.features.dashboard

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.IpBand
import kr.co.gcflarchive.admin.core.Permission
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.Navigator
import kr.co.gcflarchive.admin.ui.Route
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject

/** ① 홈(대시보드): 상태, 긴급 토글, 처리 대기함, 최근 이벤트. */
class DashboardFragment : ListFragment() {
    private var popup: JSONObject? = null

    override suspend fun load(): List<Row> = coroutineScope {
        val g = grants
        val stats = async { if (g.has(Permission.AUDITS)) runCatching { fetch("/api/admin/dashboard-stats").json() }.getOrNull() else null }
        val maintenance = async { runCatching { fetch("/api/maintenance").json().optBoolean("enabled") }.getOrNull() }
        val popupCfg = async { if (g.has(Permission.POPUP)) runCatching { fetch("/api/popup").json() }.getOrNull() else null }
        val pending = async { if (g.canModerateMap()) runCatching { fetch("/api/admin/map/pending-items").json() }.getOrNull() else null }
        val reviews = async { if (g.canModerateMap()) runCatching { org.json.JSONArray(fetch("/api/admin/map/reviews").body) }.getOrNull() else null }
        val ips = async { if (g.has(Permission.BLOCKED_IPS)) runCatching { fetch("/api/blocked-ips").json().optJSONArray("blocked").objects() }.getOrNull() else null }
        val authLog = async { if (g.has(Permission.AUDITS)) runCatching { fetch("/api/auth-block-audits?limit=20").json().optJSONArray("audits").objects() }.getOrNull() else null }
        val docs = async { if (g.has(Permission.PDFS)) runCatching { fetch("/api/documents").json().optJSONArray("documents").objects() }.getOrNull() else null }

        val rows = mutableListOf<Row>()
        val m = maintenance.await()

        // 상태 카드
        rows += Row.header(getString(R.string.dash_status))
        stats.await()?.let { s ->
            rows += Row("uptime", getString(R.string.dash_uptime), trailing = Trailing.Text(s.str("uptime")), icon = R.drawable.ic_history)
            rows += Row("accounts", getString(R.string.dash_accounts), trailing = Trailing.Text(s.optInt("accountsRegistered").toString()), icon = R.drawable.ic_person)
            rows += Row("documents", getString(R.string.dash_documents), trailing = Trailing.Text(s.optInt("totalDocuments").toString()), icon = R.drawable.ic_pdf)
        }
        rows += Row(
            "maintenance_state", getString(R.string.dash_maintenance_state),
            badges = listOf((if (m == true) "ON" else if (m == false) "OFF" else "?") to (if (m == true) Badge.DANGER else Badge.SUCCESS)),
            icon = R.drawable.ic_construction,
        )

        // 긴급 토글
        val canMaint = g.has(Permission.CREDENTIALS)
        popup = popupCfg.await()
        if (canMaint || popup != null) rows += Row.header(getString(R.string.dash_toggles))
        if (canMaint && m != null) {
            rows += Row("toggle_maintenance", getString(R.string.dash_toggle_maintenance), getString(R.string.dash_toggle_maintenance_desc),
                icon = R.drawable.ic_construction, trailing = Trailing.Switch(m))
        }
        popup?.let { p ->
            rows += Row("toggle_popup", getString(R.string.dash_toggle_popup), shortTime(p.str("startTime")) + " ~ " + shortTime(p.str("endTime")),
                icon = R.drawable.ic_campaign, trailing = Trailing.Switch(p.optBoolean("enabled")))
        }

        // 처리 대기함
        val p = pending.await()
        val ipList = ips.await()
        if (p != null || ipList != null) rows += Row.header(getString(R.string.dash_inbox))
        p?.let {
            val places = it.optJSONArray("pendingPlaces")?.length() ?: 0
            val details = it.optJSONArray("pendingDetails")?.length() ?: 0
            rows += Row("inbox_places", getString(R.string.dash_inbox_places), trailing = Trailing.Text(places.toString()), icon = R.drawable.ic_map, payload = Route.MAP_REQUESTS)
            rows += Row("inbox_hours", getString(R.string.dash_inbox_hours), trailing = Trailing.Text(details.toString()), icon = R.drawable.ic_history, payload = Route.MAP_REQUESTS)
        }
        reviews.await()?.let { rows += Row("inbox_reviews", getString(R.string.dash_inbox_reviews), trailing = Trailing.Text(it.length().toString()), icon = R.drawable.ic_notice, payload = Route.MAP_REVIEWS) }
        val autoBlocks = ipList.orEmpty().filter { IpBand.isAuto(it.str("reason")) }.sortedByDescending { it.str("created_at") }
        if (ipList != null) {
            rows += Row("inbox_auto", getString(R.string.dash_inbox_auto), autoBlocks.take(3).joinToString("\n") { "${it.str("band")} · ${shortTime(it.str("created_at"))}" }.ifBlank { null },
                trailing = Trailing.Text(autoBlocks.size.toString()), icon = R.drawable.ic_block, payload = Route.IP_BLOCKS)
        }

        // 최근 이벤트 5건 (차단 · 로그인 실패 · 업로드)
        data class Event(val time: String, val title: String, val meta: String, val badge: Pair<String, Badge>)
        val events = mutableListOf<Event>()
        autoBlocks.forEach { events += Event(it.str("created_at"), getString(R.string.event_auto_block, it.str("band")), it.str("reason"), "차단" to Badge.DANGER) }
        authLog.await().orEmpty().forEach { events += Event(it.str("timestamp"), getString(R.string.event_login_blocked, it.str("hakbun")), joinMeta(it.str("reason"), it.str("ip")), "로그인 실패" to Badge.PURPLE) }
        docs.await().orEmpty().forEach { events += Event(it.str("modified"), getString(R.string.event_upload, it.str("displayName").ifBlank { it.str("filename") }), it.str("category"), "업로드" to Badge.INFO) }
        val recent = events.filter { it.time.isNotBlank() }.sortedByDescending { it.time }.take(5)
        if (recent.isNotEmpty()) {
            rows += Row.header(getString(R.string.dash_events))
            recent.forEachIndexed { i, e -> rows += Row("event_$i", e.title, e.meta.ifBlank { null }, shortTime(e.time), badges = listOf(e.badge)) }
        }
        rows
    }

    override fun onRowClick(row: Row) {
        (row.payload as? Route)?.let { route ->
            if (route.tab == kr.co.gcflarchive.admin.ui.Tab.SECURITY) {
                (activity as? kr.co.gcflarchive.admin.ui.MainActivity)?.openTab(R.id.tab_security)
            } else {
                Navigator.open(requireContext(), route)
            }
        }
    }

    override fun onRowSwitch(row: Row, checked: Boolean) {
        when (row.id) {
            "toggle_maintenance" -> setMaintenance(checked, undoable = true)
            "toggle_popup" -> setPopup(checked, undoable = true)
        }
    }

    private fun setMaintenance(enabled: Boolean, undoable: Boolean) {
        act {
            api.postJson("/api/admin/maintenance", JSONObject().put("enabled", enabled))
            if (undoable) undo(getString(if (enabled) R.string.dash_maintenance_on else R.string.dash_maintenance_off)) { setMaintenance(!enabled, false) }
        }
    }

    private fun setPopup(enabled: Boolean, undoable: Boolean) {
        val cfg = popup ?: return
        act {
            // /api/popup replaces the whole config, so send it back with only "enabled" changed.
            api.postJson("/api/popup", JSONObject(cfg.toString()).put("enabled", enabled))
            if (undoable) undo(getString(if (enabled) R.string.dash_popup_on else R.string.dash_popup_off)) { setPopup(!enabled, false) }
        }
    }

    private fun undo(message: String, revert: () -> Unit) {
        snackbar(message, getString(R.string.undo), revert)
    }
}
