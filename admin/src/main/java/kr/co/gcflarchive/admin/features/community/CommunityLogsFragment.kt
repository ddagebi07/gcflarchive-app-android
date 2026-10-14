package kr.co.gcflarchive.admin.features.community

import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.features.flat
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject

/** 활동 기록: 누가, 언제, 무엇을 했는지 (최근 200건). 종류로 거르고 이름·내용으로 찾아요. */
class CommunityLogsFragment : ListFragment() {
    private var group = "all"

    override val searchHint get() = getString(R.string.community_logs_search)
    override val emptyText get() = getString(R.string.community_logs_empty)

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setChips(CommunityAdmin.LOG_GROUPS.map { it.first to it.second }, group) {
            group = it
            reload()
        }
    }

    override suspend fun load(): List<Row> {
        val logs = fetch("/api/community/admin/logs?limit=200").json().optJSONArray("logs").objects()
            .filter { CommunityAdmin.inGroup(group, it.str("action")) }
        setSummary(getString(R.string.community_logs_summary, logs.size))
        return logs.map { l ->
            val detail = (l.optJSONObject("detail") ?: JSONObject()).flat()
            val actor = joinMeta(l.str("actorName"), l.str("actorKey")).ifBlank { getString(R.string.community_logs_system) }
            val action = l.str("action")
            Row(
                id = l.optInt("id").toString(),
                title = "${CommunityAdmin.actionLabel(action)} · $actor",
                subtitle = CommunityAdmin.detailLine(detail).ifBlank { null },
                meta = joinMeta(CommunityAdmin.time(l.str("timestamp")), l.str("boardId"), targetOf(l), l.str("actorIp")),
                badges = listOfNotNull(badgeFor(action)),
                payload = l,
            )
        }
    }

    private fun targetOf(l: JSONObject): String? {
        val id = l.str("targetId").ifBlank { return null }
        return when (l.str("targetType")) {
            "post" -> getString(R.string.community_logs_post, id)
            "comment" -> getString(R.string.community_logs_comment, id)
            else -> null
        }
    }

    private fun badgeFor(action: String): Pair<String, Badge>? = when {
        action.startsWith("moderate_") || action == "auto_blind" -> getString(R.string.community_logs_badge_moderation) to Badge.DANGER
        action == "filter_blocked" || action == "report" -> getString(R.string.community_logs_badge_safety) to Badge.PURPLE
        else -> null
    }
}
