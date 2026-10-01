package kr.co.gcflarchive.admin.features.content

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminApi
import kr.co.gcflarchive.admin.core.AnswerGrid
import kr.co.gcflarchive.admin.databinding.ActivityAnswerSheetBinding
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.features.strings
import kr.co.gcflarchive.admin.ui.BaseActivity
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 정답지 편집: 객관식 40문항 그리드 + 서술형(최대 10개) → 미리보기(PDF) / 저장. */
class AnswerSheetActivity : BaseActivity() {
    private lateinit var binding: ActivityAnswerSheetBinding
    private lateinit var filename: String
    private val cells = MutableList(AnswerGrid.QUESTIONS) { "" }
    private val groups = mutableListOf<MaterialButtonToggleGroup>()
    private val essayInputs = mutableListOf<TextInputEditText>()
    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnswerSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        filename = intent.getStringExtra(EXTRA_FILE).orEmpty()
        supportActionBar?.title = getString(R.string.answer_edit)
        supportActionBar?.subtitle = intent.getStringExtra(EXTRA_TITLE)

        buildGrid()
        binding.btnAddEssay.setOnClickListener { addEssay("") }
        binding.btnPreview.setOnClickListener { preview() }
        binding.btnSave.setOnClickListener { save() }
        load()
    }

    private fun buildGrid() {
        for (q in 0 until AnswerGrid.QUESTIONS) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = getString(R.string.answer_q, q + 1)
                setTextAppearance(R.style.TextAppearance_Krds_DetailLabel)
                minWidth = dp(44)
            })
            val group = MaterialButtonToggleGroup(this).apply { isSingleSelection = false }
            (listOf("1", "2", "3", "4", "5", "?")).forEach { label ->
                group.addView(
                    MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        id = android.view.View.generateViewId()
                        text = label
                        tag = label
                        minWidth = 0
                        minimumWidth = 0
                        insetTop = 0
                        insetBottom = 0
                        setPadding(0, 0, 0, 0)
                    },
                    LinearLayout.LayoutParams(0, dp(40), 1f),
                )
            }
            group.addOnButtonCheckedListener { g, id, checked ->
                if (syncing) return@addOnButtonCheckedListener
                val label = g.findViewById<MaterialButton>(id).tag as String
                val digits = cells[q].filter { it.isDigit() }.toHashSet()
                cells[q] = when {
                    label == "?" -> if (checked) "?" else ""
                    checked -> (digits + label[0]).sorted().joinToString("")
                    else -> (digits - label[0]).sorted().joinToString("")
                }
                render(q)
                updatePreviewText()
            }
            groups += group
            row.addView(group, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            binding.grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) })
        }
    }

    /** Reflects cells[q] onto its toggle buttons ("?" excludes digits and vice versa). */
    private fun render(q: Int) {
        syncing = true
        val g = groups[q]
        for (i in 0 until g.childCount) {
            val b = g.getChildAt(i) as MaterialButton
            val shouldCheck = if (b.tag == "?") cells[q] == "?" else cells[q] != "?" && cells[q].contains(b.tag as String)
            if (b.isChecked != shouldCheck) {
                if (shouldCheck) g.check(b.id) else g.uncheck(b.id)
            }
        }
        syncing = false
    }

    private fun updatePreviewText() {
        binding.answersText.text = AnswerGrid.serialize(cells).ifBlank { getString(R.string.answer_none) }
    }

    private fun addEssay(text: String) {
        if (essayInputs.size >= AnswerGrid.MAX_ESSAYS) {
            Dialogs.toast(this, getString(R.string.answer_essay_max, AnswerGrid.MAX_ESSAYS))
            return
        }
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.answer_essay, essayInputs.size + 1)
            helperText = getString(R.string.answer_essay_help)
        }
        val input = TextInputEditText(layout.context).apply {
            setText(text)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        layout.addView(input)
        essayInputs += input
        binding.essays.addView(layout, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    private fun load() {
        binding.progress.isVisible = true
        lifecycleScope.launch {
            runCatching { AdminApi.get(this@AnswerSheetActivity).get("/api/admin/answer-sheet?filename=${Uri.encode(filename)}", cache = false).json() }
                .onSuccess { json ->
                    val sheet = json.optJSONObject("sheet") ?: JSONObject()
                    binding.examTitle.setText(sheet.str("examTitle").ifBlank { intent.getStringExtra(EXTRA_TITLE).orEmpty().removeSuffix(".pdf") })
                    binding.subject.setText(sheet.str("subject"))
                    binding.year.setText(sheet.str("year"))
                    binding.term.setText(sheet.str("term"))
                    binding.teachers.setText(sheet.optJSONArray("teachers").strings().joinToString(", "))
                    binding.notes.setText(sheet.str("notes"))
                    runCatching { AnswerGrid.parse(sheet.str("answers")) }.getOrNull()?.forEachIndexed { i, c -> cells[i] = c }
                    sheet.optJSONArray("essays").strings().forEach(::addEssay)
                    if (essayInputs.isEmpty()) addEssay("")
                }
                .onFailure { e -> Dialogs.toast(this@AnswerSheetActivity, Dialogs.messageOf(this@AnswerSheetActivity, e)) }
            cells.indices.forEach(::render)
            updatePreviewText()
            binding.progress.isVisible = false
        }
    }

    private fun payload(): JSONObject = JSONObject()
        .put("filename", filename)
        .put("examTitle", binding.examTitle.text.toString().trim())
        .put("subject", binding.subject.text.toString().trim())
        .put("year", binding.year.text.toString().trim())
        .put("term", binding.term.text.toString().trim())
        .put("teachers", binding.teachers.text.toString().trim())
        .put("notes", binding.notes.text.toString().trim())
        .put("answers", AnswerGrid.serialize(cells))
        .put("essays", JSONArray(essayInputs.map { it.text.toString().trim() }))

    private fun preview() {
        busy(true)
        lifecycleScope.launch {
            runCatching { AdminApi.get(this@AnswerSheetActivity).bytes("/api/admin/answer-sheet/preview", payload()) }
                .onSuccess { bytes ->
                    val dir = File(cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, "answer_preview.pdf").apply { writeBytes(bytes) }
                    val uri = FileProvider.getUriForFile(this@AnswerSheetActivity, "$packageName.files", file)
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
                .onFailure { e -> Dialogs.toast(this@AnswerSheetActivity, Dialogs.messageOf(this@AnswerSheetActivity, e)) }
            busy(false)
        }
    }

    private fun save() {
        busy(true)
        lifecycleScope.launch {
            runCatching { AdminApi.get(this@AnswerSheetActivity).postJson("/api/admin/answer-sheet", payload()) }
                .onSuccess {
                    Dialogs.toast(this@AnswerSheetActivity, getString(R.string.saved))
                    finish()
                }
                .onFailure { e -> Dialogs.toast(this@AnswerSheetActivity, Dialogs.messageOf(this@AnswerSheetActivity, e)) }
            busy(false)
        }
    }

    private fun busy(b: Boolean) {
        binding.progress.isVisible = b
        binding.btnSave.isEnabled = !b
        binding.btnPreview.isEnabled = !b
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_FILE = "file"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, filename: String, title: String): Intent =
            Intent(context, AnswerSheetActivity::class.java).putExtra(EXTRA_FILE, filename).putExtra(EXTRA_TITLE, title)
    }
}
