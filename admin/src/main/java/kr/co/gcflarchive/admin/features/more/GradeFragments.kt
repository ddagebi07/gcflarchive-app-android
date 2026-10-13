package kr.co.gcflarchive.admin.features.more

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.shortTime
import kr.co.gcflarchive.admin.features.flat
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val GRADES = "/api/grades/admin"

private val MODES = listOf(
    "closed" to "비공개",
    "sample_count" to "최소 인원 충족 시",
    "time" to "공개 시각 이후",
    "sample_count_time" to "인원 + 시각 조건",
)

/** Subjects known to the grade service (keys of the cutoff settings). */
private suspend fun gradeSubjects(fetch: suspend (String) -> JSONObject): List<String> =
    fetch("$GRADES/grade-cutoff-settings").optJSONObject("settings")?.optJSONObject("subjects")?.keys()?.asSequence()?.toList()?.sorted().orEmpty()

/** 공개 조건 (모드·최소 인원·연산자·공개 시각), 과목별 또는 여러 과목 한꺼번에. */
class GradeSettingsFragment : ListFragment() {
    private var subjects: List<String> = emptyList()
    override val fab get() = getString(R.string.gs_bulk) to R.drawable.ic_check_all

    override suspend fun load(): List<Row> {
        val all = fetch("$GRADES/grade-cutoff-settings").json().optJSONObject("settings")?.optJSONObject("subjects") ?: JSONObject()
        subjects = all.keys().asSequence().sorted().toList()
        setSummary(getString(R.string.gs_summary, subjects.size, subjects.count { all.getJSONObject(it).str("mode").let { m -> m.isNotBlank() && m != "closed" } }))
        return subjects.map { name ->
            val c = all.getJSONObject(name)
            val mode = c.str("mode").ifBlank { "closed" }
            Row(
                id = name,
                title = name,
                subtitle = describe(c),
                badges = listOf((if (mode == "closed") "비공개" else "공개 조건") to (if (mode == "closed") Badge.NEUTRAL else Badge.SUCCESS)),
                trailing = Trailing.Chevron, payload = c,
            )
        }
    }

    private fun describe(c: JSONObject): String {
        val mode = c.str("mode").ifBlank { "closed" }
        val samples = getString(R.string.gs_desc_samples, c.optInt("min_samples"))
        val time = getString(R.string.gs_desc_time, shortTime(c.str("release_at")).ifBlank { "-" })
        return when (mode) {
            "sample_count" -> samples
            "time" -> time
            "sample_count_time" -> "$samples ${if (c.str("condition_operator") == "and") "그리고" else "또는"} $time"
            else -> getString(R.string.gs_desc_closed)
        }
    }

    override fun onRowClick(row: Row) {
        if (!guardWrite()) return
        form(listOf(row.id), row.payload as JSONObject)
    }

    override fun onFab() {
        if (subjects.isEmpty()) return Dialogs.toast(requireContext(), getString(R.string.list_empty))
        form(null, JSONObject())
    }

    /** [targets] null = pick subjects in the form (일괄 설정). */
    private fun form(targets: List<String>?, c: JSONObject) {
        val sheet = FormSheet(
            requireActivity(),
            if (targets != null) getString(R.string.grade_settings_edit, targets.single()) else getString(R.string.gs_bulk),
            getString(R.string.gs_form_desc),
        )
        if (targets == null) sheet.checklist("subjects", getString(R.string.gs_bulk_subjects), subjects.map { it to it }, emptySet())
        sheet.select("mode", getString(R.string.grade_mode), MODES, c.str("mode").ifBlank { "closed" })
            .stepper("min_samples", getString(R.string.grade_min_samples), c.optInt("min_samples"), 0, 1000, step = 1)
            .dateTime("release_at", getString(R.string.grade_release_at), c.str("release_at"))
            .select("condition_operator", getString(R.string.grade_operator), listOf("or" to "또는 (하나만 충족)", "and" to "그리고 (모두 충족)"), c.str("condition_operator").ifBlank { "or" })
            .note(getString(R.string.gs_modes_note))
            .show { v ->
                val chosen = targets ?: v.list("subjects")
                if (chosen.isEmpty()) throw IllegalArgumentException(getString(R.string.gs_bulk_none))
                val mode = v.text("mode")
                val usesSamples = "sample_count" in mode
                val usesTime = "time" in mode
                if (usesSamples && v.int("min_samples") <= 0) throw IllegalArgumentException(getString(R.string.gs_need_samples))
                if (usesTime && v.instant("release_at").isBlank()) throw IllegalArgumentException(getString(R.string.gs_need_time))
                api.postJson(
                    "$GRADES/grade-cutoff-settings",
                    JSONObject().put("subjects", JSONArray(chosen)).put("mode", mode)
                        .put("min_samples", if (usesSamples) v.int("min_samples") else c.optInt("min_samples"))
                        .put("condition_operator", v.text("condition_operator"))
                        // A time is only kept when the mode uses it, so stale dates don't linger.
                        .put("release_at", if (usesTime) v.instant("release_at") else ""),
                )
                reload()
            }
    }
}

/** 등급컷 표 / 오답률 조회 (표 + 이미지 저장). */
class GradeTableFragment : ListFragment() {
    private var subjects: List<String> = emptyList()
    private var subject: String? = null
    private var verifiedOnly = false
    private var lastTable: List<Pair<String, String>> = emptyList()
    private var lastCutoffs: List<JSONObject> = emptyList()
    private var manual = false

    override fun onRowClick(row: Row) {
        val s = subject ?: return
        when (row.id) {
            "manual" -> if (guardWrite()) pickLevel(s)
            "manual_clear" -> if (guardWrite()) Dialogs.confirm(requireContext(), getString(R.string.gm_clear), getString(R.string.gm_clear_confirm, s), getString(R.string.gm_clear)) {
                act(getString(R.string.gp_saved)) {
                    api.postJson("$GRADES/manual-grade-cutoffs", JSONObject().put("subject", s).put("stats_mode", statsMode()).put("clear", true))
                }
            }
        }
    }

    private fun statsMode() = if (verifiedOnly) "verified_only" else "all"

    /** Step 1: which school year's grading (5 or 9 grades) the manual table is for. */
    private fun pickLevel(subject: String) {
        val current = ManualCutoffs.levelOf(lastCutoffs)
        val labels = ManualCutoffs.LEVELS.map { it.second }.toTypedArray()
        var chosen = ManualCutoffs.LEVELS.indexOfFirst { it.first == current }.coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.gm_level_title)
            .setSingleChoiceItems(labels, chosen) { _, which -> chosen = which }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.next) { _, _ -> manualForm(subject, ManualCutoffs.LEVELS[chosen].first) }
            .show()
    }

    /** Step 2: one cutoff per grade, prefilled from the current (manual or computed) table. */
    private fun manualForm(subject: String, level: String) {
        val values = ManualCutoffs.prefill(lastCutoffs, level)
        val sheet = FormSheet(requireActivity(), getString(R.string.gm_form_title, subject), getString(R.string.gm_form_desc, if (verifiedOnly) "인증 사용자만" else "전체"))
        for (g in 1..ManualCutoffs.gradeCount(level)) {
            sheet.text("g$g", getString(R.string.gm_grade_field, g), values.getOrNull(g - 1).orEmpty(), decimal = true)
        }
        sheet.text("sample_count", getString(R.string.gm_samples), "", hint = getString(R.string.gm_optional), number = true)
            .text("average_score", getString(R.string.gm_average), "", hint = getString(R.string.gm_optional), decimal = true)
            .show { v ->
                val rows = ManualCutoffs.build(level, (1..ManualCutoffs.gradeCount(level)).map { v.text("g$it") }).getOrThrow()
                val body = JSONObject().put("subject", subject).put("stats_mode", statsMode()).put("grade_cutoffs", rows)
                v.text("sample_count").toIntOrNull()?.let { body.put("sample_count", it) }
                v.text("average_score").toDoubleOrNull()?.let { body.put("average_score", it) }
                api.postJson("$GRADES/manual-grade-cutoffs", body)
                reload()
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                menu.add(0, 1, 0, R.string.grade_mode_verified).setCheckable(true).setChecked(verifiedOnly)
                menu.add(0, 2, 1, R.string.grade_save_image).setIcon(R.drawable.ic_share).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
                1 -> { verifiedOnly = !verifiedOnly; item.isChecked = verifiedOnly; reload(); true }
                2 -> { saveImage(); true }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override suspend fun load(): List<Row> {
        if (subjects.isEmpty()) {
            subjects = gradeSubjects { fetch(it).json() }
            subject = subject ?: subjects.firstOrNull()
            setChips(subjects.map { it to it }, subject.orEmpty()) { subject = it; reload() }
        }
        val s = subject ?: return emptyList()
        val mode = statsMode()
        val enc = android.net.Uri.encode(s)
        val t = fetch("$GRADES/grade-cutoff-table?subject=$enc&stats_mode=$mode").json()
        lastCutoffs = t.optJSONArray("grade_cutoffs").objects()
        manual = t.optBoolean("manual")
        val rows = mutableListOf<Row>()
        rows += Row("manual", getString(if (manual) R.string.gm_edit else R.string.gm_enter), getString(if (manual) R.string.gm_active_desc else R.string.gm_desc),
            icon = R.drawable.ic_edit, trailing = Trailing.Chevron,
            badges = if (manual) listOf(getString(R.string.gm_badge) to Badge.PURPLE) else emptyList())
        if (manual) rows += Row("manual_clear", getString(R.string.gm_clear), getString(R.string.gm_clear_desc), icon = R.drawable.ic_delete, trailing = Trailing.Chevron)
        rows += Row.header(getString(R.string.grade_table_header, s) + if (t.optBoolean("manual")) " (수동 입력)" else "")
        rows += Row("samples", getString(R.string.grade_samples),
            getString(R.string.grade_samples_detail, t.optInt("verified_sample_count"), t.optInt("anonymous_sample_count")),
            trailing = Trailing.Text(t.optInt("sample_count").toString()))
        rows += Row("avg", getString(R.string.grade_average), trailing = Trailing.Text(String.format("%.1f", t.optDouble("average_score", 0.0))))
        lastTable = lastCutoffs.map { c ->
            val score = if (c.isNull("cutoff_score")) "산출불가" else String.format("%.1f점", c.optDouble("cutoff_score"))
            val level = c.str("grade_level").takeIf { it.isNotBlank() }?.let { " · ${it}학년" }.orEmpty()
            c.str("grade") to score + level
        }
        rows += lastTable.mapIndexed { i, (g, rest) -> Row("cut$i", "${g}등급".replace("등급등급", "등급"), rest) }

        // 오답률
        val w = runCatching { fetch("$GRADES/wrong-question-stats?subject=$enc&stats_mode=$mode").json() }.getOrNull()
        val items = w?.let { it.optJSONArray("questions") ?: it.optJSONArray("stats") ?: it.optJSONArray("items") }.objects()
        if (items.isNotEmpty()) {
            rows += Row.header(getString(R.string.grade_wrong_header))
            rows += items.mapIndexed { i, q ->
                val f = q.flat()
                Row("wq$i", (f["question"] ?: f["number"] ?: "${i + 1}") + "번",
                    f.filterKeys { it != "question" && it != "number" }.entries.joinToString("  ") { (k, v) -> "$k $v" })
            }
        }
        return rows
    }

    /** Renders the cutoff table to a PNG and offers save/share. */
    private fun saveImage() {
        val s = subject ?: return
        if (lastTable.isEmpty()) {
            Dialogs.toast(requireContext(), getString(R.string.list_empty))
            return
        }
        val ctx = requireContext()
        val font = ResourcesCompat.getFont(ctx, R.font.pretendard_gov) ?: Typeface.DEFAULT
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f; typeface = Typeface.create(font, Typeface.BOLD); color = Color.rgb(19, 20, 22) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; typeface = font; color = Color.rgb(31, 33, 35) }
        val line = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 2f }
        val rowH = 64
        val width = 1080
        val height = 180 + rowH * lastTable.size + 60
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.WHITE)
            drawText("$s 등급컷" + if (verifiedOnly) " (인증 사용자)" else "", 60f, 100f, title)
            lastTable.forEachIndexed { i, (g, rest) ->
                val y = 180f + i * rowH
                drawLine(60f, y - 44, width - 60f, y - 44, line)
                drawText(if (g.endsWith("등급")) g else "${g}등급", 60f, y, text)
                drawText(rest, 260f, y, text)
            }
        }
        val name = "grade_cutoff_${s}_${System.currentTimeMillis() / 1000}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GCFL Admin")
            }
            ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Dialogs.toast(ctx, getString(R.string.grade_image_saved))
            }
        }
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name).apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
    }
}

/** 익명 PIN 조회·삭제 (현재 시험 / 전체). */
class GradePinsFragment : ListFragment() {
    private var scope = "current"
    override val searchHint get() = getString(R.string.grade_pin_search)
    override val selectionAction get() = getString(R.string.delete)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setChips(listOf("current" to "현재 시험", "all" to "전체"), scope) { scope = it; reload() }
    }

    override suspend fun load(): List<Row> {
        val json = fetch("$GRADES/guest-access-codes?scope=$scope").json()
        val pins = json.optJSONArray("pins").objects()
        setSummary(getString(R.string.grade_pin_summary, json.optInt("allocated_pin_count", pins.size), json.optInt("guest_count")))
        return pins.map { p ->
            Row(
                id = p.str("code"),
                title = p.str("code"),
                subtitle = joinMeta(p.str("exam_name"), p.str("grade").takeIf { it.isNotBlank() }?.let { "${it}학년" }, p.str("track")),
                meta = joinMeta(getString(R.string.grade_pin_issued, shortTime(p.str("issued_at"))), p.str("last_used_at").takeIf { it.isNotBlank() }?.let { getString(R.string.grade_pin_used, shortTime(it)) }),
                selectable = true,
            )
        }
    }

    override fun onRowClick(row: Row) = Dialogs.toast(requireContext(), getString(R.string.grade_pin_select_hint))

    override fun onSelectionAction(rows: List<Row>) {
        Dialogs.confirmSensitive(requireContext(), getString(R.string.grade_pin_delete, rows.size), getString(R.string.grade_pin_delete_message), keyword = getString(R.string.delete)) {
            endSelection()
            act(getString(R.string.deleted)) {
                api.deleteJson("$GRADES/guest-access-codes", JSONObject().put("scope", scope).put("codes", JSONArray(rows.map { it.id })))
            }
        }
    }
}

/** 성적 기록 조회·삭제, 점수 수동 입력. */
class GradeEntriesFragment : ListFragment() {
    override val searchHint get() = getString(R.string.grade_entry_search)
    override val fab get() = getString(R.string.grade_entry_add) to R.drawable.ic_add
    override val swipeLabel get() = getString(R.string.delete)
    private var currentExam = ""

    override suspend fun load(): List<Row> {
        val json = fetch("$GRADES/entries").json()
        currentExam = json.str("current_exam_name")
        val entries = json.optJSONArray("entries").objects()
        setSummary(getString(R.string.grade_entry_summary, entries.size, currentExam.ifBlank { "-" }))
        return entries.map { e ->
            val who = e.str("hakbun_display").ifBlank { e.str("hakbun").take(10) + "…" }
            Row(
                id = e.str("entry_id"),
                title = "${e.str("subject")} · ${e.str("score")}점",
                subtitle = joinMeta(who, e.str("exam_name"), e.optInt("grade").takeIf { it > 0 }?.let { "${it}등급" }),
                meta = joinMeta(shortTime(e.str("timestamp")), e.str("ip"), e.optJSONArray("wrong_questions").strings().takeIf { it.isNotEmpty() }?.let { "오답 " + it.joinToString(",") }),
                badges = listOfNotNull(
                    e.str("user_type") to (if (e.str("user_type") == "verified") Badge.SUCCESS else Badge.NEUTRAL),
                    if (e.optBoolean("is_current_exam")) "현재 시험" to Badge.INFO else null,
                ),
                swipeable = true, payload = e,
            )
        }
    }

    override fun onSwipe(row: Row) {
        val e = row.payload as JSONObject
        Dialogs.confirmSensitive(requireContext(), getString(R.string.grade_entry_delete), getString(R.string.grade_entry_delete_message, row.title), keyword = getString(R.string.delete)) {
            act(getString(R.string.deleted)) {
                api.postJson(
                    "$GRADES/delete-entry",
                    JSONObject().put("entry_id", e.str("entry_id")).put("hakbun", e.str("encrypted_hakbun"))
                        .put("subject", e.str("subject")).put("exam_name", e.str("exam_name")),
                )
            }
        }
    }

    override fun onFab() {
        FormSheet(requireActivity(), getString(R.string.grade_entry_add), getString(R.string.grade_entry_add_desc))
            .text("hakbun", getString(R.string.hakbun), number = true, required = true)
            .text("subject", getString(R.string.answer_subject), required = true)
            .text("exam_name", getString(R.string.grade_exam_name), currentExam, required = true)
            .text("score", getString(R.string.grade_score), number = true, required = true,
                validate = { if (it.toDoubleOrNull()?.let { s -> s in 0.0..100.0 } == true) null else getString(R.string.grade_score_invalid) })
            .text("wrong_questions", getString(R.string.grade_wrong_questions), hint = "3, 7, 12")
            .show { v ->
                val wrong = JSONArray(v.text("wrong_questions").split(',', ' ').mapNotNull { it.trim().toIntOrNull() })
                api.postJson(
                    "$GRADES/upsert-score",
                    JSONObject().put("hakbun", v.text("hakbun")).put("subject", v.text("subject")).put("exam_name", v.text("exam_name"))
                        .put("score", v.text("score").toDouble()).put("wrong_questions", wrong),
                )
                reload()
            }
    }
}

/**
 * 시험 정책 편집: 현재 시험명, 과목 배정 규칙, 선택과목 묶음, 과목별 문항 수·제출 시각.
 * Every change is a small form; the whole policy is saved at once (POST exam-policy).
 */
class GradePolicyFragment : ListFragment() {
    private var policy: ExamPolicy? = null
    override val swipeLabel get() = getString(R.string.delete)

    override suspend fun load(): List<Row> {
        val p = ExamPolicyCodec.parse(fetch("$GRADES/exam-policy").json().optJSONObject("policy"))
        policy = p
        val rows = mutableListOf<Row>()

        rows += Row.header(getString(R.string.gp_exam_header))
        rows += Row(
            "exam", getString(R.string.gp_exam_name), p.examName.ifBlank { getString(R.string.gp_exam_unset) },
            meta = getString(R.string.gp_exam_hint), icon = R.drawable.ic_edit, trailing = Trailing.Chevron,
            badges = if (p.examName.isBlank()) listOf(getString(R.string.gp_unset) to Badge.DANGER) else emptyList(),
        )

        rows += Row.header(getString(R.string.gp_rules_header))
        p.rules.forEachIndexed { i, r ->
            rows += Row(
                "rule:$i", ExamPolicyCodec.describeTarget(r), r.subjects.joinToString(" · "),
                meta = getString(R.string.gp_rule_order, i + 1), badges = listOf(getString(R.string.gp_subject_count, r.subjects.size) to Badge.INFO),
                trailing = Trailing.Chevron, swipeable = true, payload = i,
            )
        }
        rows += Row("rule:add", getString(R.string.gp_rule_add), getString(R.string.gp_rules_hint), icon = R.drawable.ic_add)

        rows += Row.header(getString(R.string.gp_groups_header))
        p.groups.forEachIndexed { i, g ->
            rows += Row(
                "group:$i", g.label.ifBlank { g.slot.ifBlank { getString(R.string.gp_group_unnamed) } }, g.subjects.joinToString(" / "),
                meta = g.slot.takeIf { it.isNotBlank() }?.let { getString(R.string.gp_group_slot_meta, it) },
                trailing = Trailing.Chevron, swipeable = true, payload = i,
            )
        }
        rows += Row("group:add", getString(R.string.gp_group_add), getString(R.string.gp_groups_hint), icon = R.drawable.ic_add)

        rows += Row.header(getString(R.string.gp_subjects_header))
        val subjects = ExamPolicyCodec.subjects(p)
        if (subjects.isEmpty()) rows += Row("subjects:none", getString(R.string.gp_subjects_none))
        subjects.forEach { name ->
            val q = p.questionCounts[name] ?: 0
            val d = p.descriptiveCounts[name] ?: 0
            rows += Row(
                "subject:$name", name,
                joinMeta(
                    if (q > 0) getString(R.string.gp_questions, q) + if (d > 0) getString(R.string.gp_descriptive_suffix, d) else "" else getString(R.string.gp_questions_none),
                    p.submitAfter[name]?.let { getString(R.string.gp_opens_at, shortTime(it)) } ?: getString(R.string.gp_opens_always),
                ),
                trailing = Trailing.Chevron, payload = name,
            )
        }

        rows += Row.header(getString(R.string.gp_tools_header))
        rows += Row("migrate", getString(R.string.gp_migrate), getString(R.string.gp_migrate_desc), icon = R.drawable.ic_build, trailing = Trailing.Chevron)
        rows += Row("web", getString(R.string.grade_policy_edit_web), getString(R.string.gp_web_desc), icon = R.drawable.ic_open_in_browser, trailing = Trailing.Chevron)
        return rows
    }

    private suspend fun save(p: ExamPolicy) {
        api.postJson("$GRADES/exam-policy", ExamPolicyCodec.toPayload(p))
        reload()
    }

    override fun onRowClick(row: Row) {
        val p = policy ?: return
        when {
            row.id == "web" -> androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                .launchUrl(requireContext(), android.net.Uri.parse(api.url("/admin/grades")))
            !guardWrite() -> Unit
            row.id == "exam" -> editExamName(p)
            row.id == "rule:add" -> editRule(p, null)
            row.id.startsWith("rule:") -> editRule(p, row.payload as Int)
            row.id == "group:add" -> editGroup(p, null)
            row.id.startsWith("group:") -> editGroup(p, row.payload as Int)
            row.id.startsWith("subject:") && row.payload is String -> editSubject(p, row.payload)
            row.id == "migrate" -> Dialogs.confirm(requireContext(), getString(R.string.gp_migrate), getString(R.string.gp_migrate_confirm), getString(R.string.gp_migrate_run)) {
                act(getString(R.string.gp_saved)) { api.postJson("$GRADES/exam-policy/migrate", JSONObject()) }
            }
        }
    }

    override fun onSwipe(row: Row) {
        val p = policy ?: return
        val i = row.payload as? Int ?: return
        if (row.id.startsWith("rule:")) {
            // The server keeps the old rules when an empty list arrives, so the last one can't go.
            if (p.rules.size <= 1) return Dialogs.toast(requireContext(), getString(R.string.gp_rule_last))
            Dialogs.confirm(requireContext(), getString(R.string.gp_rule_delete), row.title.toString() + "\n" + row.subtitle, getString(R.string.delete)) {
                act(getString(R.string.deleted)) { save(p.copy(rules = p.rules.filterIndexed { j, _ -> j != i })) }
            }
        } else if (row.id.startsWith("group:")) {
            Dialogs.confirm(requireContext(), getString(R.string.gp_group_delete), row.title.toString(), getString(R.string.delete)) {
                act(getString(R.string.deleted)) { save(p.copy(groups = p.groups.filterIndexed { j, _ -> j != i })) }
            }
        }
    }

    private fun editExamName(p: ExamPolicy) {
        FormSheet(requireActivity(), getString(R.string.gp_exam_name), getString(R.string.gp_exam_desc))
            .text("name", getString(R.string.gp_exam_name), p.examName, hint = getString(R.string.gp_exam_example), required = true)
            .show { v -> save(p.copy(examName = v.text("name"))) }
    }

    private fun editRule(p: ExamPolicy, index: Int?) {
        val r = index?.let { p.rules.getOrNull(it) } ?: SubjectRule("", "", "", emptyList())
        val sheet = FormSheet(
            requireActivity(),
            getString(if (index == null) R.string.gp_rule_add else R.string.gp_rule_edit),
            getString(R.string.gp_rule_desc),
        )
            .select("grade", getString(R.string.gp_rule_grade), ExamPolicyCodec.GRADE_OPTIONS, r.grade)
            .text("class_start", getString(R.string.gp_rule_class_start), r.classStart, hint = getString(R.string.gp_rule_class_hint), number = true)
            .text("class_end", getString(R.string.gp_rule_class_end), r.classEnd, number = true)
            .chips("subjects", getString(R.string.gp_rule_subjects), r.subjects, getString(R.string.gp_subject_add_hint))
        val count = p.rules.size + if (index == null) 1 else 0
        if (count > 1) sheet.stepper("order", getString(R.string.gp_rule_order_label), (index ?: p.rules.size) + 1, 1, count)
        sheet.show { v ->
            val rule = SubjectRule(v.text("grade"), v.text("class_start"), v.text("class_end"), v.list("subjects"))
            ExamPolicyCodec.validateRule(rule)?.let { throw IllegalArgumentException(it) }
            val rules = if (index == null) p.rules + rule else p.rules.mapIndexed { j, old -> if (j == index) rule else old }
            val from = index ?: rules.lastIndex
            val to = if (count > 1) v.int("order") - 1 else from
            save(p.copy(rules = ExamPolicyCodec.move(rules, from, to)))
        }
    }

    private fun editGroup(p: ExamPolicy, index: Int?) {
        val g = index?.let { p.groups.getOrNull(it) } ?: SelectionGroup("", "", emptyList())
        // Offer every subject the policy knows; subjects already in another group stay listed
        // so the validation message can explain the conflict.
        val options = (ExamPolicyCodec.subjects(p) + g.subjects).distinct().map { it to it }
        val sheet = FormSheet(
            requireActivity(),
            getString(if (index == null) R.string.gp_group_add else R.string.gp_group_edit),
            getString(R.string.gp_group_desc),
        )
            .text("label", getString(R.string.gp_group_label), g.label, hint = getString(R.string.gp_group_label_hint))
            .text("slot", getString(R.string.gp_group_slot), g.slot, hint = getString(R.string.gp_group_slot_hint))
        if (options.isNotEmpty()) sheet.checklist("subjects", getString(R.string.gp_group_subjects), options, g.subjects.toSet())
        sheet.chips("extra", getString(R.string.gp_group_extra), emptyList(), getString(R.string.gp_subject_add_hint))
        sheet.show { v ->
            val group = SelectionGroup(v.text("label"), v.text("slot"), (v.list("subjects") + v.list("extra")).distinct())
            ExamPolicyCodec.validateGroup(p.groups, index, group)?.let { throw IllegalArgumentException(it) }
            val groups = if (index == null) p.groups + group else p.groups.mapIndexed { j, old -> if (j == index) group else old }
            save(p.copy(groups = groups))
        }
    }

    private fun editSubject(p: ExamPolicy, name: String) {
        val opens = p.submitAfter[name].orEmpty()
        FormSheet(requireActivity(), getString(R.string.gp_subject_edit, name), getString(R.string.gp_subject_desc))
            .stepper("questions", getString(R.string.gp_question_count), p.questionCounts[name] ?: 0, 0, 100)
            .stepper("descriptive", getString(R.string.gp_descriptive_count), p.descriptiveCounts[name] ?: 0, 0, 30)
            .switch("limit", getString(R.string.gp_limit_submit), opens.isNotBlank(), getString(R.string.gp_limit_submit_desc))
            .dateTime("opens", getString(R.string.gp_opens_label), opens)
            .show { v ->
                val q = v.int("questions")
                val d = v.int("descriptive")
                ExamPolicyCodec.validateCounts(q, d)?.let { throw IllegalArgumentException(it) }
                val at = if (v.bool("limit")) v.instant("opens").ifBlank { throw IllegalArgumentException(getString(R.string.gp_opens_required)) } else ""
                save(
                    p.copy(
                        questionCounts = p.questionCounts + (name to q),
                        descriptiveCounts = p.descriptiveCounts + (name to d),
                        submitAfter = if (at.isBlank()) p.submitAfter - name else p.submitAfter + (name to at),
                    ),
                )
            }
    }
}
