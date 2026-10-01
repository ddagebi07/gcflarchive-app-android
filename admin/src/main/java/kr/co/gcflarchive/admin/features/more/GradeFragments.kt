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

/** 공개 조건 (모드·최소 인원·연산자·공개 시각), 과목별. */
class GradeSettingsFragment : ListFragment() {
    override suspend fun load(): List<Row> {
        val subjects = fetch("$GRADES/grade-cutoff-settings").json().optJSONObject("settings")?.optJSONObject("subjects") ?: JSONObject()
        return subjects.keys().asSequence().sorted().map { name ->
            val c = subjects.getJSONObject(name)
            val mode = c.str("mode")
            Row(
                id = name,
                title = name,
                subtitle = joinMeta(
                    MODES.firstOrNull { it.first == mode }?.second ?: mode,
                    if ("sample_count" in mode) "최소 ${c.optInt("min_samples")}명" else null,
                    if (mode == "sample_count_time") c.str("condition_operator").uppercase() else null,
                    if ("time" in mode) shortTime(c.str("release_at")) else null,
                ),
                badges = listOf((if (mode == "closed") "비공개" else "공개 조건") to (if (mode == "closed") Badge.NEUTRAL else Badge.SUCCESS)),
                trailing = Trailing.Chevron, payload = c,
            )
        }.toList()
    }

    override fun onRowClick(row: Row) {
        if (!guardWrite()) return
        val c = row.payload as JSONObject
        FormSheet(requireActivity(), getString(R.string.grade_settings_edit, row.id))
            .select("mode", getString(R.string.grade_mode), MODES, c.str("mode").ifBlank { "closed" })
            .stepper("min_samples", getString(R.string.grade_min_samples), c.optInt("min_samples"), 0, 1000)
            .select("condition_operator", getString(R.string.grade_operator), listOf("or" to "OR (하나만 충족)", "and" to "AND (모두 충족)"), c.str("condition_operator").ifBlank { "or" })
            .dateTime("release_at", getString(R.string.grade_release_at), c.str("release_at"))
            .show { v ->
                api.postJson(
                    "$GRADES/grade-cutoff-settings",
                    JSONObject().put("subject", row.id).put("mode", v.text("mode")).put("min_samples", v.int("min_samples"))
                        .put("condition_operator", v.text("condition_operator")).put("release_at", v.instant("release_at")),
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
        val mode = if (verifiedOnly) "verified_only" else "all"
        val enc = android.net.Uri.encode(s)
        val t = fetch("$GRADES/grade-cutoff-table?subject=$enc&stats_mode=$mode").json()
        val rows = mutableListOf<Row>()
        rows += Row.header(getString(R.string.grade_table_header, s) + if (t.optBoolean("manual")) " (수동 입력)" else "")
        rows += Row("samples", getString(R.string.grade_samples),
            getString(R.string.grade_samples_detail, t.optInt("verified_sample_count"), t.optInt("anonymous_sample_count")),
            trailing = Trailing.Text(t.optInt("sample_count").toString()))
        rows += Row("avg", getString(R.string.grade_average), trailing = Trailing.Text(String.format("%.1f", t.optDouble("average_score", 0.0))))
        lastTable = t.optJSONArray("grade_cutoffs").objects().map { c ->
            val f = c.flat()
            val grade = f["grade"] ?: f["등급"] ?: ""
            grade to f.filterKeys { it != "grade" && it != "등급" }.entries.joinToString("  ") { (k, v) -> "$k $v" }
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

/** JSON 정책·문항 수 설정 — 읽기 전용 + "웹에서 편집". */
class GradePolicyFragment : ListFragment() {
    override suspend fun load(): List<Row> {
        val policy = fetch("$GRADES/exam-policy").json().optJSONObject("policy") ?: JSONObject()
        val rows = mutableListOf(
            Row("web", getString(R.string.grade_policy_edit_web), getString(R.string.grade_policy_readonly), icon = R.drawable.ic_open_in_browser, trailing = Trailing.Chevron),
        )
        policy.keys().forEach { k ->
            val v = policy.opt(k)
            rows += Row("p:$k", k, when (v) {
                is JSONObject -> v.toString(2)
                is JSONArray -> v.toString(2)
                else -> v.toString()
            }, trailing = Trailing.Chevron)
        }
        return rows
    }

    override fun onRowClick(row: Row) {
        if (row.id == "web") {
            androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                .launchUrl(requireContext(), android.net.Uri.parse(api.url("/admin/grades")))
        } else {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(row.title).setMessage(row.subtitle).setPositiveButton(android.R.string.ok, null).show()
        }
    }
}
