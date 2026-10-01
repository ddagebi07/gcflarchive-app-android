package kr.co.gcflarchive.admin.features.logs

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.google.android.material.datepicker.MaterialDatePicker
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.Csv
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.flat
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import kr.co.gcflarchive.admin.ui.kit.joinMeta
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/** The audit endpoints; each returns {"<listKey>": [...]} and accepts ?limit=. */
enum class AuditKind(val path: String, val listKey: String, val title: String, val dateFilter: Boolean = false) {
    ACCESS("/api/download-audits", "audits", "접근·활동 로그", dateFilter = true),
    AUTH_BLOCK("/api/auth-block-audits", "audits", "차단 시도 로그"),
    DRIVE("/api/temp-share-audits", "audits", "극플드라이브 기록"),
    SCHOOL_IP("/api/drive-client-logs", "logs", "학교 IP 호출 로그"),
    CONSENT("/api/consent-audits", "audits", "가입(개인정보 동의) 기록"),
    SHORT_LINK("/api/short-link-audits", "audits", "단축 링크 사용 기록"),
}

/**
 * Generic audit log viewer. The server has no offset paging, only ?limit=, so "더 보기"
 * (infinite scroll) re-fetches with a larger limit. Search filters by IP/학번/파일명 etc.
 */
class AuditLogFragment : ListFragment() {
    private val kind get() = AuditKind.valueOf(requireArguments().getString(ARG_KIND)!!)
    private var limit = PAGE
    private var startDate: String? = null
    private var endDate: String? = null
    private var entries: List<JSONObject> = emptyList()

    override val searchHint get() = getString(R.string.audit_search_hint)
    override val emptyText get() = getString(R.string.audit_empty)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                if (kind.dateFilter) menu.add(0, 2, 0, R.string.audit_period).setIcon(R.drawable.ic_filter).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                menu.add(0, 1, 1, R.string.audit_export).setIcon(R.drawable.ic_share).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }

            // Hidden bottom-tab fragments stay RESUMED; only show actions while on screen.
            override fun onPrepareMenu(menu: Menu) {
                val shown = view?.isShown == true
                menu.findItem(1)?.isVisible = shown
                menu.findItem(2)?.isVisible = shown
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
                1 -> { exportCsv(); true }
                2 -> { pickPeriod(); true }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override suspend fun load(): List<Row> {
        val q = buildString {
            append(kind.path).append("?limit=").append(limit)
            startDate?.let { append("&startDate=").append(it) }
            endDate?.let { append("&endDate=").append(it) }
        }
        val json = fetch(q).json()
        entries = json.optJSONArray(kind.listKey).objects().sortedByDescending { timeOf(it) }
        setSummary(
            listOfNotNull(
                getString(R.string.audit_summary, entries.size),
                if (startDate != null) "${startDate} ~ ${endDate ?: ""}" else null,
            ).joinToString(" · "),
        )
        val rows = entries.mapIndexed { i, o -> rowFor(i, o) }.toMutableList()
        if (entries.size >= limit) {
            rows += Row("more", getString(R.string.audit_more, PAGE), trailing = Trailing.Chevron, icon = R.drawable.ic_add)
        }
        return rows
    }

    /** Puts the fields that matter first; everything else is still searchable/exported. */
    private fun rowFor(i: Int, o: JSONObject): Row {
        val f = o.flat()
        val who = f["userId"] ?: f["hakbun"] ?: f["uploader"] ?: f["user"] ?: ""
        val what = f["originalName"] ?: f["filename"] ?: f["title"] ?: f["slug"] ?: f["action"] ?: f["event"] ?: f["path"] ?: ""
        val outcome = f["outcome"] ?: f["reason"] ?: f["action"] ?: f["event"] ?: ""
        val time = timeOf(o)
        return Row(
            id = "e$i",
            title = what.ifBlank { outcome.ifBlank { kind.title } },
            subtitle = joinMeta(who.takeIf { it.isNotBlank() && it != "anonymous" }, f["ip"]),
            meta = joinMeta(shortTime(time), f["status"]?.let { "HTTP $it" }),
            badges = listOfNotNull(outcome.takeIf { it.isNotBlank() && it != what }?.let { it to badgeFor(it) }),
            payload = o,
        )
    }

    private fun badgeFor(outcome: String): Badge = when {
        outcome.contains("block") || outcome.contains("denied") || outcome.contains("fail") -> Badge.DANGER
        outcome.contains("download") || outcome.contains("view") -> Badge.INFO
        else -> Badge.NEUTRAL
    }

    private fun timeOf(o: JSONObject): String =
        listOf("timestamp", "accessedAt", "upload_time", "created_at", "time").firstNotNullOfOrNull { k -> o.optString(k).takeIf { it.isNotBlank() } }.orEmpty()

    override fun onRowClick(row: Row) {
        if (row.id == "more") {
            limit += PAGE
            reload()
            return
        }
        val o = row.payload as? JSONObject ?: return
        val text = o.flat().entries.joinToString("\n") { (k, v) -> "$k: $v" }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(row.title)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun pickPeriod() {
        val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText(R.string.audit_period).build()
        picker.addOnPositiveButtonClickListener { range ->
            fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
            startDate = range.first?.let(::day)
            endDate = range.second?.let(::day)
            limit = PAGE
            reload()
        }
        picker.addOnNegativeButtonClickListener {
            startDate = null
            endDate = null
            reload()
        }
        picker.show(childFragmentManager, "period")
    }

    /** CSV 내보내기 → 안드로이드 공유 시트. */
    private fun exportCsv() {
        if (entries.isEmpty()) {
            Dialogs.toast(requireContext(), getString(R.string.audit_empty))
            return
        }
        val ctx = requireContext()
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // never leave old exports behind
        val file = File(dir, "${kind.name.lowercase()}_${System.currentTimeMillis() / 1000}.csv")
        file.writeText(Csv.build(entries.map { it.flat() }, preferred = listOf("timestamp", "timestampKst", "userId", "hakbun", "ip")))
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, kind.title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                getString(R.string.audit_export),
            ),
        )
    }

    companion object {
        private const val ARG_KIND = "kind"
        private const val PAGE = 200

        fun of(kind: AuditKind) = AuditLogFragment().apply { arguments = Bundle().apply { putString(ARG_KIND, kind.name) } }
    }
}
