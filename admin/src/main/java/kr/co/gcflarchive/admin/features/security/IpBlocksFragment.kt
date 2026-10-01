package kr.co.gcflarchive.admin.features.security

import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.IpBand
import kr.co.gcflarchive.admin.core.shortTime
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

/** IP 차단: 목록(AUTO 배지, 스와이프 해제), 수동 차단 추가, 자동 차단 설정. */
class IpBlocksFragment : ListFragment() {
    override val searchHint get() = getString(R.string.ip_search_hint)
    override val fab get() = getString(R.string.ip_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.unblock)
    override val emptyText get() = getString(R.string.ip_empty)
    private var config: JSONObject? = null

    override suspend fun load(): List<Row> {
        config = runCatching { fetch("/api/admin/auto-ip-block-config").json() }.getOrNull()
        val list = fetch("/api/blocked-ips").json().optJSONArray("blocked").objects()
            .sortedByDescending { it.str("last_attempt_at").ifBlank { it.str("created_at") } }
        val autoCount = list.count { IpBand.isAuto(it.str("reason")) }
        setSummary(getString(R.string.ip_summary, list.size, autoCount))
        val rows = mutableListOf<Row>()
        config?.let { c ->
            rows += Row(
                "auto_config", getString(R.string.ip_auto_config),
                getString(R.string.ip_auto_config_desc, c.optInt("threshold"), c.optInt("windowMinutes"), c.optJSONArray("keywords")?.length() ?: 0),
                icon = R.drawable.ic_settings,
                badges = listOf((if (c.optBoolean("enabled")) "사용 중" else "꺼짐") to (if (c.optBoolean("enabled")) Badge.SUCCESS else Badge.NEUTRAL)),
                trailing = Trailing.Chevron,
            )
        }
        rows += list.map { o ->
            val auto = IpBand.isAuto(o.str("reason"))
            Row(
                id = o.str("band"),
                title = o.str("band"),
                subtitle = o.str("reason").ifBlank { null },
                meta = joinMeta(
                    getString(R.string.ip_attempts, o.optInt("attempts")),
                    o.str("last_attempt_at").takeIf { it.isNotBlank() }?.let { getString(R.string.ip_last_attempt, shortTime(it)) },
                    shortTime(o.str("created_at")),
                ),
                badges = listOfNotNull(if (auto) "AUTO" to Badge.PURPLE else null, o.str("band_type") to Badge.NEUTRAL),
                swipeable = true,
                payload = o,
            )
        }
        return rows
    }

    override fun onRowClick(row: Row) {
        if (row.id == "auto_config") openConfig() else onSwipe(row)
    }

    override fun onSwipe(row: Row) {
        if (row.id == "auto_config") return
        Dialogs.confirm(requireContext(), getString(R.string.unblock), getString(R.string.ip_unblock_confirm, row.id), getString(R.string.unblock)) {
            act(getString(R.string.ip_unblocked, row.id)) { api.deleteJson("/api/blocked-ips", JSONObject().put("band", row.id)) }
        }
    }

    override fun onFab() {
        FormSheet(requireActivity(), getString(R.string.ip_add), getString(R.string.ip_add_desc))
            .text("band", getString(R.string.ip_band), hint = getString(R.string.ip_band_hint), required = true,
                validate = { if (IpBand.isValid(it)) null else getString(R.string.ip_band_invalid) })
            .text("reason", getString(R.string.reason), multiline = true)
            .submitText(getString(R.string.block))
            .show { v ->
                api.postJson("/api/blocked-ips", JSONObject().put("band", v.text("band")).put("reason", v.text("reason")))
                reload()
            }
    }

    private fun openConfig() {
        val c = config ?: return
        if (!guardWrite()) return
        FormSheet(requireActivity(), getString(R.string.ip_auto_config), getString(R.string.ip_auto_config_help))
            .switch("enabled", getString(R.string.ip_auto_enabled), c.optBoolean("enabled"))
            .stepper("threshold", getString(R.string.ip_auto_threshold), c.optInt("threshold", 3), 1, 1000)
            .stepper("windowMinutes", getString(R.string.ip_auto_window), c.optInt("windowMinutes", 60), 1, 10080, step = 5)
            .chips("keywords", getString(R.string.ip_auto_keywords), c.optJSONArray("keywords").strings(), getString(R.string.ip_auto_keyword_add))
            .show { v ->
                api.postJson(
                    "/api/admin/auto-ip-block-config",
                    JSONObject()
                        .put("enabled", v.bool("enabled"))
                        .put("threshold", v.int("threshold"))
                        .put("windowMinutes", v.int("windowMinutes"))
                        .put("keywords", jsonArray(v.list("keywords"))),
                )
                reload()
            }
    }
}
