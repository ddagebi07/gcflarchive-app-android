package kr.co.gcflarchive.admin.features.content

import android.webkit.WebView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.ui.kit.Badge
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.FormSheet
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 팝업 공지: 활성화·노출 기간·본문(HTML 허용)·미리보기·저장·새 ID·기본값 초기화. */
class PopupFragment : ListFragment() {
    private var cfg = JSONObject()

    override suspend fun load(): List<Row> {
        cfg = fetch("/api/popup").json()
        val now = Instant.now().toString()
        val live = cfg.optBoolean("enabled") && cfg.str("startTime") <= now && (cfg.str("endTime").isBlank() || now <= cfg.str("endTime"))
        return listOf(
            Row.header(getString(R.string.popup_status)),
            Row("enabled", getString(R.string.popup_enabled), if (live) getString(R.string.popup_live) else getString(R.string.popup_not_live),
                badges = listOf((if (live) "노출 중" else "미노출") to (if (live) Badge.SUCCESS else Badge.NEUTRAL)),
                trailing = Trailing.Switch(cfg.optBoolean("enabled"))),
            Row("period", getString(R.string.popup_period), "${shortTime(cfg.str("startTime"))} ~ ${shortTime(cfg.str("endTime"))}", icon = R.drawable.ic_history),
            Row("content", getString(R.string.popup_content), cfg.str("content").take(200).ifBlank { getString(R.string.popup_no_content) },
                badges = listOf((if (cfg.optBoolean("isHtml")) "HTML" else "텍스트") to Badge.INFO), icon = R.drawable.ic_campaign),
            Row("id", getString(R.string.popup_id), cfg.str("id").ifBlank { "-" }, meta = getString(R.string.popup_id_desc)),
            Row.header(getString(R.string.popup_actions)),
            Row("edit", getString(R.string.popup_edit), icon = R.drawable.ic_edit, trailing = Trailing.Chevron),
            Row("preview", getString(R.string.preview), icon = R.drawable.ic_visibility, trailing = Trailing.Chevron),
            Row("new_id", getString(R.string.popup_new_id), getString(R.string.popup_new_id_desc), icon = R.drawable.ic_refresh),
            Row("reset", getString(R.string.popup_reset), icon = R.drawable.ic_delete),
        )
    }

    private fun newId(): String = "popup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    private fun save(next: JSONObject, message: String) = act(message) { api.postJson("/api/popup", next) }

    override fun onRowSwitch(row: Row, checked: Boolean) {
        if (row.id == "enabled") save(JSONObject(cfg.toString()).put("enabled", checked), getString(R.string.saved))
    }

    override fun onRowClick(row: Row) {
        when (row.id) {
            "edit", "period", "content" -> edit()
            "preview" -> preview(cfg.str("content"), cfg.optBoolean("isHtml"))
            "new_id" -> Dialogs.confirm(requireContext(), getString(R.string.popup_new_id), getString(R.string.popup_new_id_confirm), getString(R.string.confirm_do)) {
                save(JSONObject(cfg.toString()).put("id", newId()), getString(R.string.saved))
            }
            "reset" -> Dialogs.confirm(requireContext(), getString(R.string.popup_reset), getString(R.string.popup_reset_confirm), getString(R.string.popup_reset)) {
                val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
                save(
                    JSONObject().put("id", newId()).put("enabled", false)
                        .put("startTime", now.toString()).put("endTime", now.plus(1, ChronoUnit.DAYS).toString())
                        .put("isHtml", true).put("content", ""),
                    getString(R.string.saved),
                )
            }
        }
    }

    private fun edit() {
        if (!guardWrite()) return
        FormSheet(requireActivity(), getString(R.string.popup_edit))
            .switch("enabled", getString(R.string.popup_enabled), cfg.optBoolean("enabled"))
            .dateTime("startTime", getString(R.string.popup_start), cfg.str("startTime"))
            .dateTime("endTime", getString(R.string.popup_end), cfg.str("endTime"))
            .switch("isHtml", getString(R.string.popup_html), cfg.optBoolean("isHtml"), getString(R.string.popup_html_desc))
            .text("content", getString(R.string.popup_content), cfg.str("content"), multiline = true)
            .show { v ->
                val start = v.instant("startTime")
                val end = v.instant("endTime")
                require(start.isBlank() || end.isBlank() || start < end) { getString(R.string.popup_period_invalid) }
                api.postJson(
                    "/api/popup",
                    JSONObject().put("id", cfg.str("id").ifBlank { newId() })
                        .put("enabled", v.bool("enabled"))
                        .put("startTime", start).put("endTime", end)
                        .put("isHtml", v.bool("isHtml"))
                        .put("content", v.text("content")),
                )
                reload()
            }
    }

    private fun preview(content: String, html: Boolean) {
        val view = if (html) {
            WebView(requireContext()).apply {
                settings.javaScriptEnabled = false
                loadDataWithBaseURL(api.baseUrl, "<meta name=viewport content='width=device-width'>$content", "text/html", "utf-8", null)
            }
        } else {
            TextView(requireContext()).apply { text = content; setPadding(48, 24, 48, 24) }
        }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.preview).setView(view).setPositiveButton(android.R.string.ok, null).show()
    }
}
