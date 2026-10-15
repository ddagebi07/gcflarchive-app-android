package kr.co.gcflarchive.app.survey

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.community.CommunityParser
import kr.co.gcflarchive.app.databinding.ActivitySurveyBinding
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Shared frame of the 설문조사 screens: toolbar, progress, a vertical [content]. */
abstract class SurveyBaseActivity : AppCompatActivity() {
    protected lateinit var binding: ActivitySurveyBinding
    internal val v by lazy { SurveyViews(this) }
    protected val content: LinearLayout get() = binding.content

    protected val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    abstract fun load()

    protected fun add(view: View, top: Int = 12) = content.addView(view, v.lp(top))

    protected fun busy(on: Boolean) {
        binding.progress.isVisible = on
    }

    protected fun snack(message: String) = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()

    /** For messages shown while the screen closes. */
    protected fun toast(message: String) = android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()

    /** A full-screen message with one action (retry / login). */
    protected fun showState(title: String, desc: String?, action: String?, onAction: (() -> Unit)?) {
        content.removeAllViews()
        add(v.sectionTitle(title).apply { gravity = android.view.Gravity.CENTER }, 64)
        desc?.let { add(v.meta(it).apply { gravity = android.view.Gravity.CENTER }, 8) }
        if (action != null && onAction != null) add(v.secondary(action) { onAction() }, 16)
    }

    protected fun failed(e: Throwable, what: String) {
        if (e is CancellationException) throw e
        if (e is SurveyException && e.loginRequired) {
            showState(getString(R.string.survey_login_needed), null, getString(R.string.login_action)) { login.launch(LoginActivity.intent(this)) }
        } else {
            showState(what, e.message, getString(R.string.retry)) { load() }
        }
    }

    protected fun time(iso: String) = CommunityParser.displayTime(iso)
}

// ── 목록 ─────────────────────────────────────────────────────────────────

/** 설문조사: 참여할 수 있는 설문과 내가 만든 설문, 만들 수 있으면 '설문 만들기'. */
class SurveyListActivity : SurveyBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setTitle(R.string.survey_title)
        binding.fab.setOnClickListener { startActivity(SurveyBuilderActivity.intent(this, 0)) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun load() {
        busy(true)
        lifecycleScope.launch {
            val result = runCatching { SurveyRepository.list() }
            busy(false)
            result.onSuccess(::render).onFailure { failed(it, getString(R.string.survey_load_failed)) }
        }
    }

    private fun render(list: SurveyList) {
        content.removeAllViews()
        binding.fab.isVisible = list.canCreate
        if (!list.loggedIn) {
            showState(getString(R.string.survey_login_needed), null, getString(R.string.login_action)) { login.launch(LoginActivity.intent(this)) }
            return
        }
        add(v.sectionTitle(getString(R.string.survey_available)), 8)
        if (list.surveys.isEmpty()) add(v.meta(getString(R.string.survey_available_empty)), 8)
        list.surveys.forEach { add(card(it), 8) }
        if (list.canCreate || list.mine.isNotEmpty()) {
            add(v.sectionTitle(getString(R.string.survey_mine)), 24)
            if (list.mine.isEmpty()) add(v.meta(getString(R.string.survey_mine_empty)), 8)
            list.mine.forEach { add(card(it), 8) }
        }
    }

    private fun card(s: SurveySummary): View {
        val c = v.card()
        val head = v.row(
            v.cardTitle(s.title).apply { maxLines = 2 } to v.weighted(8),
            v.text(s.statusLabel, 13f, bold = true, color = if (s.status == "open") R.color.krds_badge_success_fg else R.color.krds_badge_neutral_fg).apply {
                setBackgroundResource(if (s.status == "open") R.drawable.bg_krds_badge_success else R.drawable.bg_krds_badge_neutral)
                setPadding(v.dp(8), v.dp(2), v.dp(8), v.dp(2))
            } to LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        c.addView(head)
        if (s.description.isNotBlank()) c.addView(v.text(s.description, 15f).apply { maxLines = 2 }, v.lp(4))
        c.addView(v.meta(listOfNotNull(
            s.ownerName.ifBlank { null },
            getString(R.string.survey_questions, s.questionCount),
            s.responseCount?.let { getString(R.string.survey_responses, it) },
            if (s.closesAt.isNotBlank() && s.status == "open") getString(R.string.survey_closes, time(s.closesAt)) else null,
            if (s.responded) getString(R.string.survey_responded) else null,
        ).joinToString(" · ")), v.lp(6))
        c.isClickable = true
        c.isFocusable = true
        c.foreground = getDrawable(android.R.drawable.list_selector_background)
        c.setOnClickListener { startActivity(SurveyActivity.intent(this, s.id)) }
        return c
    }

    companion object {
        fun intent(context: Context) = Intent(context, SurveyListActivity::class.java)
    }
}

// ── 응답하기 ─────────────────────────────────────────────────────────────

/** 설문 응답 (구글 폼처럼 문항마다 카드). 만든 사람에게는 결과·수정·마감·삭제 메뉴. */
class SurveyActivity : SurveyBaseActivity() {
    private var id = 0
    private var form: SurveyForm? = null
    private val answers = mutableMapOf<String, Answer>()
    private var editing = false
    private var submitted = false
    private var problem: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setTitle(R.string.survey_title)
        id = intent.getIntExtra(EXTRA_ID, 0)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun load() {
        busy(true)
        lifecycleScope.launch {
            val result = runCatching { SurveyRepository.form(id) }
            busy(false)
            result.onSuccess { f ->
                form = f
                if (!editing) {
                    answers.clear()
                    f.myAnswers?.let(answers::putAll)
                }
                invalidateOptionsMenu()
                render(f)
            }.onFailure { failed(it, getString(R.string.survey_load_failed)) }
        }
    }

    private fun render(f: SurveyForm) {
        content.removeAllViews()
        val answered = f.myAnswers != null
        val inputEnabled = f.canRespond && (!answered || editing)

        val (head, inner) = v.headCard()
        inner.addView(v.pageTitle(f.summary.title))
        if (f.description.isNotBlank()) inner.addView(v.text(f.description, 16f), v.lp(8))
        inner.addView(v.meta(listOfNotNull(
            f.summary.ownerName.ifBlank { null }, f.summary.statusLabel,
            if (f.summary.closesAt.isNotBlank()) getString(R.string.survey_closes, time(f.summary.closesAt)) else null,
        ).joinToString(" · ")), v.lp(8))
        if (f.questions.any { it.required }) inner.addView(v.error(getString(R.string.survey_required_note)), v.lp(4))
        add(head, 8)

        if (f.summary.collectIdentity) add(v.info(getString(R.string.survey_identity_notice)))
        if (!f.accepting && !f.summary.canManage) {
            add(v.info(getString(if (f.rawStatus == "draft") R.string.survey_not_published else R.string.survey_closed)))
        }
        if (answered && !editing) {
            add(v.info(getString(if (submitted) R.string.survey_thanks else R.string.survey_already)))
            val buttons = mutableListOf<Pair<View, LinearLayout.LayoutParams>>()
            if (f.canRespond) buttons += v.secondary(getString(R.string.survey_edit_answer)) { editing = true; render(f) } to v.weighted(8)
            if (f.canSeeSummary) buttons += v.secondary(getString(R.string.survey_see_summary)) {
                startActivity(SurveyResultsActivity.intent(this, id))
            } to v.weighted()
            if (buttons.isNotEmpty()) add(v.row(*buttons.toTypedArray()), 8)
        }

        f.questions.forEach { q -> add(questionView(q, inputEnabled)) }

        if (inputEnabled) {
            problem?.takeIf { it.first.isEmpty() }?.let { add(v.error(it.second)) }
            val buttons = mutableListOf<Pair<View, LinearLayout.LayoutParams>>()
            if (editing) buttons += v.secondary(getString(R.string.survey_cancel)) {
                editing = false; problem = null
                answers.clear(); f.myAnswers?.let(answers::putAll)
                render(f)
            } to v.weighted(8)
            buttons += v.primary(getString(if (editing) R.string.survey_submit_edit else R.string.survey_submit)) { submit(f) } to v.weighted()
            add(v.row(*buttons.toTypedArray()), 16)
        }
    }

    private fun questionView(q: SurveyQuestion, enabled: Boolean): View {
        if (q.type == QuestionType.SECTION) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            box.addView(View(this).apply { setBackgroundColor(getColor(R.color.krds_text_strong)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, v.dp(2)))
            box.addView(v.sectionTitle(q.title), v.lp(8))
            if (q.description.isNotBlank()) box.addView(v.text(q.description, 15f), v.lp(4))
            return box
        }
        val err = problem?.takeIf { it.first == q.id }?.second
        val c = v.card(error = err != null)
        c.addView(v.cardTitle(q.title + if (q.required) " *" else ""))
        if (q.description.isNotBlank()) c.addView(v.meta(q.description), v.lp(4))
        val current = answers[q.id]
        fun set(a: Answer?) {
            if (a == null || a.isBlank()) answers.remove(q.id) else answers[q.id] = a
            if (problem?.first == q.id) problem = null
        }
        when (q.type) {
            QuestionType.SHORT, QuestionType.PARAGRAPH -> c.addView(
                v.field(getString(if (q.type == QuestionType.SHORT) R.string.survey_short_hint else R.string.survey_long_hint),
                    (current as? Answer.Text)?.value.orEmpty(), if (q.type == QuestionType.SHORT) 500 else 5000,
                    multiline = q.type == QuestionType.PARAGRAPH) { set(Answer.Text(it)) }.apply { isEnabled = enabled },
                v.lp(8),
            )
            QuestionType.CHOICE -> c.addView(choiceGroup(q, (current as? Answer.Text)?.value, enabled) { set(it?.let { s -> Answer.Text(s) }) }, v.lp(4))
            QuestionType.CHECKBOX -> checkboxes(c, q, (current as? Answer.Picks)?.values.orEmpty(), enabled) { set(if (it.isEmpty()) null else Answer.Picks(it)) }
            QuestionType.DROPDOWN -> c.addView(
                v.dropdown(getString(R.string.survey_dropdown_hint), listOf("" to getString(R.string.survey_dropdown_none)) + q.options.map { it to it },
                    (current as? Answer.Text)?.value.orEmpty()) { set(if (it.isBlank()) null else Answer.Text(it)) }.apply { isEnabled = enabled },
                v.lp(8),
            )
            QuestionType.SCALE -> scale(c, q, (current as? Answer.Number)?.value, enabled) { set(Answer.Number(it)) }
            QuestionType.DATE -> c.addView(v.secondary((current as? Answer.Text)?.value ?: getString(R.string.survey_date_hint)) {
                val today = LocalDate.now()
                val start = runCatching { LocalDate.parse((answers[q.id] as? Answer.Text)?.value) }.getOrDefault(today)
                DatePickerDialog(this, { _, y, m, d -> set(Answer.Text("%04d-%02d-%02d".format(y, m + 1, d))); render(form!!) },
                    start.year, start.monthValue - 1, start.dayOfMonth).show()
            }.apply { isEnabled = enabled }, v.lp(8))
            QuestionType.TIME -> c.addView(v.secondary((current as? Answer.Text)?.value ?: getString(R.string.survey_time_hint)) {
                val parts = (answers[q.id] as? Answer.Text)?.value?.split(":")
                TimePickerDialog(this, { _, h, m -> set(Answer.Text("%02d:%02d".format(h, m))); render(form!!) },
                    parts?.getOrNull(0)?.toIntOrNull() ?: 9, parts?.getOrNull(1)?.toIntOrNull() ?: 0, true).show()
            }.apply { isEnabled = enabled }, v.lp(8))
            QuestionType.SECTION -> Unit
        }
        if (err != null) c.addView(v.error(err), v.lp(8))
        return c
    }

    /** 객관식: radio rows, plus '기타' with its own text when allowed. */
    private fun choiceGroup(q: SurveyQuestion, picked: String?, enabled: Boolean, onChange: (String?) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val group = RadioGroup(this)
        val otherValue = picked?.takeIf { it !in q.options }
        val buttons = q.options.map { option ->
            MaterialRadioButton(this).apply {
                text = option
                id = View.generateViewId()
                minHeight = v.dp(48)
                isEnabled = enabled
                setTextColor(getColor(R.color.krds_text_strong))
            }.also { group.addView(it) }
        }
        var otherButton: RadioButton? = null
        var otherText = otherValue.orEmpty()
        if (q.other) {
            otherButton = MaterialRadioButton(this).apply {
                text = getString(R.string.survey_other)
                id = View.generateViewId()
                minHeight = v.dp(48)
                isEnabled = enabled
                setTextColor(getColor(R.color.krds_text_strong))
            }.also { group.addView(it) }
        }
        buttons.forEachIndexed { i, b -> if (q.options[i] == picked) b.isChecked = true }
        if (otherValue != null) otherButton?.isChecked = true
        group.setOnCheckedChangeListener { _, checkedId ->
            val i = buttons.indexOfFirst { it.id == checkedId }
            onChange(if (i >= 0) q.options[i] else otherText.ifBlank { null })
        }
        box.addView(group)
        if (q.other) {
            box.addView(v.field(getString(R.string.survey_other), otherText, 200) { t ->
                otherText = t
                if (t.isNotBlank()) { if (otherButton?.isChecked != true) otherButton?.isChecked = true; onChange(t.trim()) }
            }.apply { isEnabled = enabled }, v.lp(4))
        }
        return box
    }

    private fun checkboxes(card: LinearLayout, q: SurveyQuestion, picked: List<String>, enabled: Boolean, onChange: (List<String>) -> Unit) {
        val chosen = picked.filter { it in q.options }.toMutableSet()
        var otherText = picked.firstOrNull { it !in q.options }.orEmpty()
        var otherOn = otherText.isNotEmpty()
        fun emit() = onChange(q.options.filter { it in chosen } + if (otherOn && otherText.isNotBlank()) listOf(otherText.trim()) else emptyList())
        q.options.forEach { option ->
            card.addView(v.check(option, option in chosen) { on -> if (on) chosen += option else chosen -= option; emit() }.apply { isEnabled = enabled })
        }
        if (q.other) {
            card.addView(v.check(getString(R.string.survey_other), otherOn) { on -> otherOn = on; emit() }.apply { isEnabled = enabled })
            card.addView(v.field(getString(R.string.survey_other), otherText, 200) { t -> otherText = t; if (t.isNotBlank()) otherOn = true; emit() }
                .apply { isEnabled = enabled }, v.lp(4))
        }
    }

    /** 선형 배율: numbered radio buttons in a row, end labels underneath. */
    private fun scale(card: LinearLayout, q: SurveyQuestion, value: Int?, enabled: Boolean, onChange: (Int) -> Unit) {
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        (q.min..q.max).forEach { n ->
            group.addView(MaterialRadioButton(this).apply {
                text = "$n"
                id = View.generateViewId()
                isEnabled = enabled
                isChecked = value == n
                minHeight = v.dp(48)
                setTextColor(getColor(R.color.krds_text_strong))
                setOnClickListener { onChange(n) }
            }, RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(android.widget.HorizontalScrollView(this).apply { addView(group) }, v.lp(4))
        if (q.minLabel.isNotBlank() || q.maxLabel.isNotBlank()) {
            card.addView(v.row(
                v.meta(q.minLabel) to v.weighted(),
                v.meta(q.maxLabel) to LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            ))
        }
    }

    private fun submit(f: SurveyForm) {
        problem = SurveyLogic.firstProblem(f.questions, answers)
        if (problem != null) {
            render(f)
            return
        }
        busy(true)
        lifecycleScope.launch {
            runCatching { SurveyRepository.submit(id, answers.toMap()) }
                .onSuccess { updated ->
                    snack(getString(if (updated) R.string.survey_updated else R.string.survey_submitted))
                    submitted = true
                    editing = false
                    load()
                }
                .onFailure { e ->
                    busy(false)
                    if (e is CancellationException) throw e
                    if (e is SurveyException && e.loginRequired) login.launch(LoginActivity.intent(this@SurveyActivity))
                    else { problem = "" to (e.message ?: getString(R.string.network_error)); render(f) }
                }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val f = form ?: return false
        if (!f.summary.canManage) return false
        menu.add(0, 1, 0, R.string.survey_menu_results)
        menu.add(0, 2, 1, R.string.survey_menu_edit)
        menu.add(0, 3, 2, when (f.rawStatus) {
            "open" -> R.string.survey_menu_close
            "draft" -> R.string.survey_menu_publish
            else -> R.string.survey_menu_reopen
        })
        menu.add(0, 4, 3, R.string.survey_menu_delete)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val f = form ?: return super.onOptionsItemSelected(item)
        when (item.itemId) {
            1 -> startActivity(SurveyResultsActivity.intent(this, id))
            2 -> startActivity(SurveyBuilderActivity.intent(this, id))
            3 -> change {
                val next = if (f.rawStatus == "open") "closed" else "open"
                SurveyRepository.setStatus(id, next)
                getString(if (next == "closed") R.string.survey_closed_done else R.string.survey_opened_done)
            }
            4 -> MaterialAlertDialogBuilder(this)
                .setMessage(R.string.survey_delete_confirm)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.survey_menu_delete) { _, _ ->
                    lifecycleScope.launch {
                        runCatching { SurveyRepository.delete(id) }
                            .onSuccess { toast(getString(R.string.survey_deleted)); finish() }
                            .onFailure { snack(it.message ?: getString(R.string.network_error)) }
                    }
                }
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun change(block: suspend () -> String) {
        lifecycleScope.launch {
            runCatching { block() }.onSuccess { snack(it); load() }.onFailure { snack(it.message ?: getString(R.string.network_error)) }
        }
    }

    companion object {
        private const val EXTRA_ID = "survey_id"
        fun intent(context: Context, id: Int) = Intent(context, SurveyActivity::class.java).putExtra(EXTRA_ID, id)
    }
}

// ── 결과 ─────────────────────────────────────────────────────────────────

/** 결과: 문항별 요약(막대·평균·주관식). 만든 사람은 개별 응답 보기와 CSV 받기도 돼요. */
class SurveyResultsActivity : SurveyBaseActivity() {
    private var id = 0
    private var results: SurveyResults? = null
    private var tab = 0
    private var index = 0

    private val saveCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching {
                val bytes = SurveyRepository.csv(id)
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("save failed")
            }.onSuccess { snack(getString(R.string.survey_csv_saved)) }
                .onFailure { snack(it.message ?: getString(R.string.network_error)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setTitle(R.string.survey_results)
        id = intent.getIntExtra(EXTRA_ID, 0)
        load()
    }

    override fun load() {
        busy(true)
        lifecycleScope.launch {
            val r = runCatching { SurveyRepository.results(id) }
            busy(false)
            r.onSuccess { results = it; render(it) }.onFailure { failed(it, getString(R.string.survey_load_failed)) }
        }
    }

    private fun render(res: SurveyResults) {
        content.removeAllViews()
        val owner = res.questions.isNotEmpty()
        add(v.pageTitle(res.title), 8)
        add(v.sectionTitle(getString(R.string.survey_responses, res.responseCount)), 8)
        if (owner) {
            fun tabButton(label: Int, which: Int) =
                (if (tab == which) v.primary(getString(label)) {} else v.secondary(getString(label)) { tab = which; render(res) })
            add(v.row(tabButton(R.string.survey_tab_summary, 0) to v.weighted(8), tabButton(R.string.survey_tab_each, 1) to v.weighted()))
            add(v.secondary(getString(R.string.survey_csv)) { saveCsv.launch("survey-$id.csv") }, 8)
        }
        if (res.responseCount == 0) {
            add(v.meta(getString(R.string.survey_no_responses)))
            return
        }
        if (tab == 0 || !owner) {
            res.summary.forEach { add(summaryCard(it)) }
            return
        }
        index = index.coerceIn(0, res.responses.size - 1)
        val row = res.responses[index]
        add(v.row(
            v.secondary(getString(R.string.survey_prev)) { index--; render(res) }.apply { isEnabled = index > 0 } to v.weighted(8),
            v.text(getString(R.string.survey_page, index + 1, res.responses.size), 15f, color = R.color.krds_text_strong).apply {
                gravity = android.view.Gravity.CENTER
            } to v.weighted(8),
            v.secondary(getString(R.string.survey_next)) { index++; render(res) }.apply { isEnabled = index < res.responses.size - 1 } to v.weighted(),
        ))
        val c = v.card()
        c.addView(v.meta(listOfNotNull(
            if (res.collectIdentity && row.respondent.isNotBlank()) getString(R.string.survey_hakbun, row.respondent) else null,
            time(row.submittedAt),
        ).joinToString(" · ")))
        res.questions.forEach { (qid, title) ->
            c.addView(v.text(title, 15f, bold = true, color = R.color.krds_text_strong), v.lp(12))
            c.addView(v.text(row.answers[qid].orEmpty().ifBlank { getString(R.string.survey_no_answer) }, 16f), v.lp(2))
        }
        add(c)
    }

    private fun summaryCard(s: QuestionSummary): View {
        val c = v.card()
        c.addView(v.cardTitle(s.title))
        c.addView(v.meta(getString(R.string.survey_answered, s.answered)), v.lp(2))
        when {
            s.options.isNotEmpty() -> {
                val total = s.options.sumOf { it.count }.coerceAtLeast(1)
                s.options.forEach { o -> c.addView(v.bar(o.text, o.count, o.count * 100 / total), v.lp(6)) }
                if (s.otherTexts.isNotEmpty()) {
                    c.addView(v.text(getString(R.string.survey_other_answers), 15f, bold = true, color = R.color.krds_text_strong), v.lp(10))
                    s.otherTexts.take(20).forEach { c.addView(v.text("· $it", 15f), v.lp(2)) }
                }
            }
            s.scale.isNotEmpty() -> {
                s.average?.let { c.addView(v.text(getString(R.string.survey_average, it.toString()), 15f, bold = true, color = R.color.krds_text_strong), v.lp(6)) }
                val total = s.scale.sumOf { it.second }.coerceAtLeast(1)
                s.scale.forEachIndexed { i, (value, count) ->
                    val label = when (i) {
                        0 -> listOf("$value", s.minLabel).filter { it.isNotBlank() }.joinToString(" ")
                        s.scale.size - 1 -> listOf("$value", s.maxLabel).filter { it.isNotBlank() }.joinToString(" ")
                        else -> "$value"
                    }
                    c.addView(v.bar(label, count, count * 100 / total), v.lp(6))
                }
            }
            else -> {
                if (s.texts.isEmpty()) c.addView(v.meta(getString(R.string.survey_no_responses)), v.lp(6))
                s.texts.take(50).forEach { t ->
                    c.addView(v.text(t, 15f), v.lp(8))
                    c.addView(View(this).apply { setBackgroundColor(getColor(R.color.krds_border)) },
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { topMargin = v.dp(8) })
                }
            }
        }
        return c
    }

    companion object {
        private const val EXTRA_ID = "survey_id"
        fun intent(context: Context, id: Int) = Intent(context, SurveyResultsActivity::class.java).putExtra(EXTRA_ID, id)
    }
}

// ── 만들기·수정 ──────────────────────────────────────────────────────────

/** 설문 만들기·수정 (구글 폼처럼): 제목·설명, 설정, 문항 카드, 순서 바꾸기, 임시 저장 / 게시하기. */
class SurveyBuilderActivity : SurveyBaseActivity() {
    private var id = 0
    private var scopes: List<ScopeOption> = emptyList()
    private var title = ""
    private var description = ""
    private var audience = mutableSetOf("login")
    private var deadlineHours = 0
    private var keepCloses = ""
    private var status = "draft"
    private var collectIdentity = false
    private var allowEdit = false
    private var showSummary = false
    private val questions = mutableListOf<SurveyQuestion>()
    private var nextId = 1
    private var error: String? = null
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getIntExtra(EXTRA_ID, 0)
        supportActionBar?.setTitle(if (id > 0) R.string.survey_edit else R.string.survey_create)
        load()
    }

    private fun newQuestion(type: QuestionType) = SurveyQuestion(
        "n${nextId++}", type, "",
        options = if (type.hasOptions) listOf(getString(R.string.survey_default_option, 1)) else emptyList(),
    )

    override fun load() {
        busy(true)
        lifecycleScope.launch {
            runCatching {
                scopes = SurveyRepository.list().scopes
                if (id > 0) {
                    val f = SurveyRepository.form(id)
                    title = f.summary.title; description = f.description; status = f.rawStatus
                    audience = f.audienceScope.toMutableSet().ifEmpty { mutableSetOf("login") }
                    keepCloses = f.summary.closesAt
                    collectIdentity = f.summary.collectIdentity; allowEdit = f.allowEdit; showSummary = f.showSummary
                    questions.clear(); questions.addAll(f.questions)
                } else if (questions.isEmpty()) {
                    questions += newQuestion(QuestionType.CHOICE)
                }
            }.onSuccess { loaded = true; busy(false); render() }
                .onFailure { busy(false); failed(it, getString(R.string.survey_load_failed)) }
        }
    }

    private fun render() {
        if (!loaded) return
        content.removeAllViews()
        val (head, inner) = v.headCard()
        inner.addView(v.field(getString(R.string.survey_form_title), title, 200) { title = it })
        inner.addView(v.field(getString(R.string.survey_form_desc), description, 2000, multiline = true) { description = it }, v.lp(8))
        add(head, 8)

        val settings = v.card()
        settings.addView(v.cardTitle(getString(R.string.survey_settings)))
        settings.addView(v.text(getString(R.string.survey_audience), 15f, bold = true, color = R.color.krds_text_strong), v.lp(8))
        settings.addView(v.meta(getString(R.string.survey_audience_desc)))
        scopes.ifEmpty { listOf(ScopeOption("login", "로그인한 사람")) }.forEach { o ->
            settings.addView(v.check(o.label, o.value in audience) { on -> if (on) audience += o.value else audience -= o.value })
        }
        val deadlines = listOf(0 to "마감 없음", 24 to "하루 뒤", 72 to "3일 뒤", 168 to "일주일 뒤", 336 to "2주 뒤", 720 to "30일 뒤") +
            listOfNotNull(if (keepCloses.isNotBlank()) -1 to getString(R.string.survey_deadline_keep, time(keepCloses)) else null)
        settings.addView(v.dropdown(getString(R.string.survey_deadline), deadlines, if (deadlineHours == 0 && keepCloses.isNotBlank()) -1 else deadlineHours) { h ->
            // -1 keeps the deadline already saved; any other choice replaces it.
            if (h == -1) deadlineHours = 0 else { deadlineHours = h; keepCloses = "" }
        }, v.lp(8))
        settings.addView(v.switchRow(getString(R.string.survey_collect), getString(R.string.survey_collect_desc), collectIdentity) { collectIdentity = it }, v.lp(8))
        settings.addView(v.switchRow(getString(R.string.survey_allow_edit), getString(R.string.survey_allow_edit_desc), allowEdit) { allowEdit = it }, v.lp(4))
        settings.addView(v.switchRow(getString(R.string.survey_show_summary), getString(R.string.survey_show_summary_desc), showSummary) { showSummary = it }, v.lp(4))
        add(settings)

        questions.indices.forEach { i -> add(questionEditor(i)) }
        add(v.row(
            v.secondary(getString(R.string.survey_add_question)) { questions += newQuestion(QuestionType.CHOICE); render() } to v.weighted(8),
            v.secondary(getString(R.string.survey_add_section)) { questions += newQuestion(QuestionType.SECTION); render() } to v.weighted(),
        ))
        error?.let { add(v.error(it)) }
        if (id > 0) {
            add(v.primary(getString(R.string.survey_save)) { save(null) }, 16)
        } else {
            add(v.row(
                v.secondary(getString(R.string.survey_save_draft)) { save(false) } to v.weighted(8),
                v.primary(getString(R.string.survey_publish)) { save(true) } to v.weighted(),
            ), 16)
        }
    }

    private fun update(i: Int, block: (SurveyQuestion) -> SurveyQuestion) {
        questions[i] = block(questions[i])
    }

    private fun questionEditor(i: Int): View {
        val q = questions[i]
        val c = v.card()
        val label = if (q.type == QuestionType.SECTION) getString(R.string.survey_q_section) else getString(R.string.survey_q_number, i + 1)
        fun icon(res: Int, desc: Int, enabled: Boolean, tint: Int = R.color.krds_text_secondary, onClick: () -> Unit) =
            android.widget.ImageButton(this).apply {
                setImageResource(res)
                setColorFilter(getColor(tint))
                setBackgroundResource(android.R.color.transparent)
                contentDescription = getString(desc)
                minimumWidth = v.dp(48); minimumHeight = v.dp(48)
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.3f
                setOnClickListener { onClick() }
            }
        val wrap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        c.addView(v.row(
            v.meta(label) to v.weighted(),
            icon(R.drawable.ic_arrow_up, R.string.survey_q_up, i > 0) { questions.add(i - 1, questions.removeAt(i)); render() } to wrap,
            icon(R.drawable.ic_arrow_down, R.string.survey_q_down, i < questions.size - 1) { questions.add(i + 1, questions.removeAt(i)); render() } to wrap,
            icon(R.drawable.ic_delete, R.string.survey_q_delete, true, R.color.krds_danger) { questions.removeAt(i); render() } to wrap,
        ))
        if (q.type != QuestionType.SECTION) {
            c.addView(v.dropdown(getString(R.string.survey_q_type), QuestionType.entries.filter { it != QuestionType.SECTION }.map { it to it.label }, q.type) { t ->
                update(i) { it.copy(type = t, options = if (t.hasOptions && it.options.isEmpty()) listOf(getString(R.string.survey_default_option, 1)) else it.options) }
                render()
            }, v.lp(8))
        }
        c.addView(v.field(getString(if (q.type == QuestionType.SECTION) R.string.survey_q_section_title else R.string.survey_q_title), q.title, 300) { t ->
            update(i) { it.copy(title = t) }
        }, v.lp(8))
        c.addView(v.field(getString(R.string.survey_q_desc), q.description, 2000) { t -> update(i) { it.copy(description = t) } }, v.lp(8))
        if (q.type.hasOptions) {
            q.options.forEachIndexed { j, o ->
                val field = v.field(getString(R.string.survey_q_option, j + 1), o, 200) { t ->
                    update(i) { cur -> cur.copy(options = cur.options.toMutableList().also { list -> if (j < list.size) list[j] = t }) }
                }
                val remove = icon(R.drawable.ic_close, R.string.survey_q_option_delete, q.options.size > 1) {
                    update(i) { cur -> cur.copy(options = cur.options.toMutableList().also { it.removeAt(j) }) }
                    render()
                }
                c.addView(v.row(field to v.weighted(4), remove to wrap), v.lp(8))
            }
            if (q.options.size < 50) c.addView(v.secondary(getString(R.string.survey_q_add_option)) {
                update(i) { it.copy(options = it.options + getString(R.string.survey_default_option, it.options.size + 1)) }
                render()
            }, v.lp(8))
            if (q.type != QuestionType.DROPDOWN) c.addView(v.check(getString(R.string.survey_q_other), q.other) { on -> update(i) { it.copy(other = on) } })
        }
        if (q.type == QuestionType.SCALE) {
            c.addView(v.row(
                v.dropdown(getString(R.string.survey_q_scale_from), listOf(0 to "0", 1 to "1"), q.min) { n -> update(i) { it.copy(min = n) }; render() } to v.weighted(8),
                v.dropdown(getString(R.string.survey_q_scale_to), (2..10).map { it to "$it" }, q.max) { n -> update(i) { it.copy(max = n) }; render() } to v.weighted(),
            ), v.lp(8))
            c.addView(v.field(getString(R.string.survey_q_scale_label, q.min), q.minLabel, 60) { t -> update(i) { it.copy(minLabel = t) } }, v.lp(8))
            c.addView(v.field(getString(R.string.survey_q_scale_label, q.max), q.maxLabel, 60) { t -> update(i) { it.copy(maxLabel = t) } }, v.lp(8))
        }
        if (q.type != QuestionType.SECTION) {
            c.addView(v.switchRow(getString(R.string.survey_q_required), null, q.required) { on -> update(i) { it.copy(required = on) } }, v.lp(8))
        }
        return c
    }

    private fun save(publish: Boolean?) {
        val closes = if (deadlineHours > 0) Instant.now().plus(deadlineHours.toLong(), ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString() else keepCloses
        val draft = SurveyDraft(
            title, description, audience.toList(), if (publish == null) status else if (publish) "open" else "draft",
            closes, collectIdentity, allowEdit, showSummary,
            questions.map { q ->
                q.copy(
                    required = q.required && q.type != QuestionType.SECTION,
                    options = if (q.type.hasOptions) q.options else emptyList(),
                    other = q.other && (q.type == QuestionType.CHOICE || q.type == QuestionType.CHECKBOX),
                )
            },
        )
        error = SurveyLogic.draftProblem(draft)
        if (error != null) {
            render()
            binding.scroll.post { binding.scroll.fullScroll(View.FOCUS_DOWN) }
            return
        }
        busy(true)
        lifecycleScope.launch {
            runCatching { if (id > 0) { SurveyRepository.update(id, draft); id } else SurveyRepository.create(draft) }
                .onSuccess { sid ->
                    busy(false)
                    toast(getString(if (draft.status == "open") R.string.survey_published else R.string.survey_saved))
                    if (id == 0 && sid > 0) startActivity(SurveyActivity.intent(this@SurveyBuilderActivity, sid))
                    finish()
                }
                .onFailure { e ->
                    busy(false)
                    if (e is CancellationException) throw e
                    error = e.message ?: getString(R.string.network_error)
                    render()
                }
        }
    }

    companion object {
        private const val EXTRA_ID = "survey_id"
        fun intent(context: Context, id: Int) = Intent(context, SurveyBuilderActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
