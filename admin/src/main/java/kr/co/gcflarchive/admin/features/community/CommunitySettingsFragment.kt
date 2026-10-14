package kr.co.gcflarchive.admin.features.community

import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import org.json.JSONObject

/** 운영 설정: 신고 몇 번이면 숨길지, 익명 글쓰기 전체 스위치, 연달아 글 올리는 간격. */
class CommunitySettingsFragment : ListFragment() {
    private var settings = JSONObject()

    override suspend fun load(): List<Row> {
        settings = fetch("/api/community/admin/settings").json().optJSONObject("settings") ?: JSONObject()
        val threshold = settings.optInt(THRESHOLD, 3)
        val interval = settings.optInt(INTERVAL, 15)
        return listOf(
            Row(
                id = THRESHOLD,
                title = getString(R.string.community_setting_threshold),
                subtitle = getString(R.string.community_setting_threshold_desc),
                trailing = Trailing.Text(if (threshold == 0) getString(R.string.community_setting_off) else getString(R.string.community_setting_times, threshold)),
            ),
            Row(
                id = ANONYMOUS,
                title = getString(R.string.community_setting_anonymous),
                subtitle = getString(R.string.community_setting_anonymous_desc),
                trailing = Trailing.Switch(settings.optBoolean(ANONYMOUS)),
            ),
            Row(
                id = INTERVAL,
                title = getString(R.string.community_setting_interval),
                subtitle = getString(R.string.community_setting_interval_desc),
                trailing = Trailing.Text(if (interval == 0) getString(R.string.community_setting_off) else getString(R.string.community_setting_seconds, interval)),
            ),
        )
    }

    override fun onRowSwitch(row: Row, checked: Boolean) {
        if (row.id == ANONYMOUS) save(JSONObject().put(ANONYMOUS, checked))
    }

    override fun onRowClick(row: Row) {
        if (!guardWrite()) return
        when (row.id) {
            THRESHOLD -> FormSheet(requireActivity(), row.title.toString(), getString(R.string.community_setting_threshold_desc))
                .stepper(THRESHOLD, getString(R.string.community_setting_threshold_unit), settings.optInt(THRESHOLD, 3), 0, 100)
                .submitText(getString(R.string.save))
                .show { v -> putSettings(JSONObject().put(THRESHOLD, v.int(THRESHOLD))) }
            INTERVAL -> FormSheet(requireActivity(), row.title.toString(), getString(R.string.community_setting_interval_desc))
                .stepper(INTERVAL, getString(R.string.community_setting_interval_unit), settings.optInt(INTERVAL, 15), 0, 600, step = 5)
                .submitText(getString(R.string.save))
                .show { v -> putSettings(JSONObject().put(INTERVAL, v.int(INTERVAL))) }
        }
    }

    private fun save(changes: JSONObject) = act(getString(R.string.community_setting_saved)) { putSettings(changes) }

    private suspend fun putSettings(changes: JSONObject) {
        api.putJson("/api/community/admin/settings", changes)
        reload()
    }

    private companion object {
        const val THRESHOLD = "report_threshold"
        const val ANONYMOUS = "anonymous_enabled"
        const val INTERVAL = "post_interval_seconds"
    }
}
