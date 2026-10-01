package kr.co.gcflarchive.admin.features.logs

import android.content.ClipData
import android.content.ClipboardManager
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.features.strings
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject

/** 단축 링크 현황: 링크·대상·사용자, 탭하면 복사, 스와이프 삭제. */
class ShortLinksFragment : ListFragment() {
    override val searchHint get() = getString(R.string.shortlink_search_hint)
    override val swipeLabel get() = getString(R.string.delete)

    override suspend fun load(): List<Row> {
        val links = fetch("/api/short-links?limit=1000").json().optJSONArray("links").objects()
        setSummary(getString(R.string.audit_summary, links.size))
        return links.map { o ->
            val users = o.optJSONArray("recent_users").strings()
            Row(
                id = o.str("slug"),
                title = o.str("shortUrl").ifBlank { o.str("slug") },
                subtitle = o.str("target").ifBlank { o.str("filename").ifBlank { o.str("url") } }.ifBlank { null },
                meta = joinMeta(shortTime(o.str("created_at")), if (users.isNotEmpty()) getString(R.string.shortlink_users, users.size, users.take(5).joinToString(", ")) else null),
                badges = listOfNotNull(o.str("outcome").takeIf { it.isNotBlank() }?.let { it to Badge.NEUTRAL }),
                swipeable = true,
                payload = o,
            )
        }
    }

    override fun onRowClick(row: Row) {
        requireContext().getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("link", row.title))
        Dialogs.toast(requireContext(), getString(R.string.copied))
    }

    override fun onSwipe(row: Row) {
        Dialogs.confirm(requireContext(), getString(R.string.delete), getString(R.string.shortlink_delete_confirm, row.id), getString(R.string.delete)) {
            act(getString(R.string.deleted)) { api.postJson("/api/shortener/delete", JSONObject().put("slug", row.id)) }
        }
    }
}
