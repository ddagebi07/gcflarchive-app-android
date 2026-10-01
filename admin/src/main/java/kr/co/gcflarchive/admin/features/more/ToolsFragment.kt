package kr.co.gcflarchive.admin.features.more

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject

/** 미니서비스: 등록 목록 조회·삭제 (등록·zip 업로드는 웹). */
class ToolsFragment : ListFragment() {
    override val searchHint get() = getString(R.string.tool_search_hint)
    override val swipeLabel get() = getString(R.string.delete)

    override suspend fun load(): List<Row> {
        val tools = fetch("/api/admin/tools/list").json().optJSONArray("tools").objects()
        setSummary(getString(R.string.tool_summary, tools.size))
        return tools.map { t ->
            Row(
                id = t.str("id"),
                title = t.str("title").ifBlank { t.str("id") },
                subtitle = t.str("description").ifBlank { null },
                meta = joinMeta(t.str("id"), shortTime(t.str("created_at"))),
                badges = listOfNotNull(t.str("category").takeIf { it.isNotBlank() }?.let { it to Badge.NEUTRAL }, if (t.has("admin_url")) "관리 페이지" to Badge.INFO else null),
                swipeable = true, payload = t,
            )
        }
    }

    override fun onRowClick(row: Row) {
        val t = row.payload as JSONObject
        val path = t.str("admin_url").ifBlank { "/tools/app/${row.id}/" }
        CustomTabsIntent.Builder().build().launchUrl(requireContext(), Uri.parse(api.url(path)))
    }

    override fun onSwipe(row: Row) {
        Dialogs.confirmSensitive(requireContext(), getString(R.string.tool_delete), getString(R.string.tool_delete_message, row.title), keyword = row.id) {
            act(getString(R.string.deleted)) { api.postJson("/api/admin/tools/delete", JSONObject().put("id", row.id)) }
        }
    }
}
