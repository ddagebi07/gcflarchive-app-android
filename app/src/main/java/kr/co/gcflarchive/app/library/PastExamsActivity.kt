package kr.co.gcflarchive.app.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.data.LoginRequiredException
import kr.co.gcflarchive.app.databinding.ItemExamBinding
import kr.co.gcflarchive.app.databinding.SheetExamFiltersBinding

/** 기출문제 아카이브 — native version of past-exams.html. */
class PastExamsActivity : LibraryListActivity() {
    override val pageTitleRes = R.string.exam_page_title
    override val pageDescRes = R.string.exam_page_desc
    override val searchHintRes = R.string.exam_search_hint

    private var all: List<LibraryDocument> = emptyList()
    private var subjects = SubjectConfig(emptyList(), ExamFilters.DEFAULT_DEPARTMENTS)
    /** null = not logged in (favorites unavailable). */
    private var favorites: MutableSet<String>? = null
    private var filters = ExamFilters()
    private val adapter = ExamAdapter()

    override fun setUpList() {
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.actionButton.visibility = View.VISIBLE
        binding.actionButton.setIconResource(R.drawable.ic_filter)
        binding.actionButton.setOnClickListener { openFilterSheet() }
    }

    override suspend fun fetch() {
        all = LibraryRepository.documents().filter { it.isPastExam && !it.isPrivate }
        subjects = runCatching { LibraryRepository.subjects() }.getOrDefault(subjects)
        favorites = runCatching { LibraryRepository.favorites().toMutableSet() }.getOrNull()
    }

    override fun render() {
        val favs = favorites.orEmpty()
        val shown = all.filter { filters.matches(it, favs, query) }.sortedWith(ExamFilters.ORDER)
        adapter.submit(shown)
        setTotal(shown.size, R.string.exam_total)
        binding.actionButton.text = if (filters.activeCount > 0) {
            getString(R.string.exam_filter_count, filters.activeCount)
        } else {
            getString(R.string.exam_filter)
        }
        renderFilterBar()
        showEmptyIfNeeded(shown.isEmpty())
    }

    /** Pinned chip row: ★ 즐겨찾기만 + one removable chip per active filter (.applied-filter-bar). */
    private fun renderFilterBar() {
        val group = binding.filterChips
        group.removeAllViews()
        if (favorites != null) {
            group.addView(chip(getString(R.string.exam_favorites_only), filters.favoritesOnly) { checked ->
                filters = filters.copy(favoritesOnly = checked)
                render()
            })
        }
        fun removable(label: String, remove: () -> ExamFilters) {
            val c = chip(label, true) { }
            c.isCloseIconVisible = true
            c.setOnCloseIconClickListener { filters = remove(); render() }
            c.setOnClickListener { filters = remove(); render() }
            group.addView(c)
        }
        filters.years.sortedDescending().forEach { y -> removable("${y}년") { filters.copy(years = filters.years - y) } }
        filters.grades.sorted().forEach { g -> removable("${g}학년") { filters.copy(grades = filters.grades - g) } }
        filters.departments.forEach { d -> removable(d) { filters.copy(departments = filters.departments - d) } }
        filters.periods.forEach { p -> removable(p) { filters.copy(periods = filters.periods - p) } }
        filters.subjects.forEach { s -> removable(s) { filters.copy(subjects = filters.subjects - s) } }
        binding.filterScroll.visibility = if (group.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun chip(label: String, checked: Boolean, onChange: (Boolean) -> Unit): Chip =
        (layoutInflater.inflate(R.layout.item_krds_filter_chip, binding.filterChips, false) as Chip).apply {
            id = View.generateViewId()
            text = label
            isCheckable = true
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }

    // ── 상세 조건 검색 (filter sheet) ───────────────────────────────────────

    private fun openFilterSheet() {
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_Krds_BottomSheet)
        val b = SheetExamFiltersBinding.inflate(layoutInflater)
        dialog.setContentView(b.root)
        var draft = filters

        fun refreshApply() {
            val count = all.count { draft.matches(it, favorites.orEmpty(), query) }
            b.btnApply.text = getString(R.string.exam_filter_apply, count)
        }

        fun <T> section(title: String, options: List<Pair<String, T>>, selected: () -> Set<T>, toggle: (T, Boolean) -> Unit) {
            val label = TextView(this).apply {
                text = title
                setTextAppearance(R.style.TextAppearance_Krds_DetailLabel)
                setTextColor(ContextCompat.getColor(context, R.color.krds_text_strong))
            }
            b.sections.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
            val group = ChipGroup(this).apply { chipSpacingVertical = dp(6); chipSpacingHorizontal = dp(6) }
            options.forEach { (text, value) ->
                group.addView(sheetChip(group, text, value in selected()) { checked ->
                    toggle(value, checked)
                    refreshApply()
                })
            }
            b.sections.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }

        fun build() {
            b.sections.removeAllViews()
            val years = all.map { it.examMeta.year }.filter { it > 0 }.distinct().sortedDescending()
            if (years.isNotEmpty()) {
                section(getString(R.string.exam_filter_year), years.map { "${it}년" to it }, { draft.years }) { v, on ->
                    draft = draft.copy(years = if (on) draft.years + v else draft.years - v)
                }
            }
            section(getString(R.string.exam_filter_grade), ExamFilters.GRADES.map { "${it}학년" to it }, { draft.grades }) { v, on ->
                draft = draft.copy(grades = if (on) draft.grades + v else draft.grades - v)
            }
            section(getString(R.string.exam_filter_department), subjects.departments.map { it to it }, { draft.departments }) { v, on ->
                draft = draft.copy(departments = if (on) draft.departments + v else draft.departments - v)
            }
            section(getString(R.string.exam_filter_period), ExamFilters.PERIODS.map { it to it }, { draft.periods }) { v, on ->
                draft = draft.copy(periods = if (on) draft.periods + v else draft.periods - v)
            }
            // 과목: one row per 계열; tapping the 계열 label selects/clears the whole row (like the web).
            subjects.groups.forEachIndexed { i, g ->
                section(
                    if (i == 0) getString(R.string.exam_filter_subject) + " · " + g.label else g.label,
                    g.subjects.map { it to it },
                    { draft.subjects },
                ) { v, on -> draft = draft.copy(subjects = if (on) draft.subjects + v else draft.subjects - v) }
                val label = b.sections.getChildAt(b.sections.childCount - 2) as TextView
                label.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_check_all, 0)
                label.compoundDrawablePadding = dp(4)
                label.setOnClickListener {
                    val allOn = g.subjects.all { it in draft.subjects }
                    draft = draft.copy(subjects = if (allOn) draft.subjects - g.subjects.toSet() else draft.subjects + g.subjects)
                    build()
                    refreshApply()
                }
            }
        }

        b.btnReset.setOnClickListener {
            draft = ExamFilters(favoritesOnly = draft.favoritesOnly)
            build()
            refreshApply()
        }
        b.btnApply.setOnClickListener {
            filters = draft
            render()
            dialog.dismiss()
        }
        build()
        refreshApply()
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }

    private fun sheetChip(parent: ViewGroup, text: String, checked: Boolean, onChange: (Boolean) -> Unit): Chip =
        (layoutInflater.inflate(R.layout.item_krds_filter_chip, parent, false) as Chip).apply {
            id = View.generateViewId()
            this.text = text
            isCheckable = true
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }

    // ── Favorites ────────────────────────────────────────────────────────

    private fun toggleFavorite(doc: LibraryDocument) {
        if (favorites == null) {
            Toast.makeText(this, R.string.exam_favorite_login, Toast.LENGTH_SHORT).show()
            requestLogin()
            return
        }
        lifecycleScope.launch {
            runCatching { LibraryRepository.toggleFavorite(doc.filename) }
                .onSuccess { starred ->
                    favorites?.let { set ->
                        if (starred) set.add(doc.filename) else set.remove(doc.filename)
                    }
                    render()
                }
                .onFailure { e ->
                    if (e is LoginRequiredException) requestLogin()
                    else Toast.makeText(this@PastExamsActivity, e.message, Toast.LENGTH_SHORT).show()
                }
        }
    }

    // ── 기출문제 상세 정보 ───────────────────────────────────────────────────

    private fun openDetail(doc: LibraryDocument) {
        val m = doc.examMeta
        val sheet = DetailSheet(this, getString(R.string.exam_detail_heading), doc.title)
            .pair(
                getString(R.string.exam_detail_year_grade),
                listOfNotNull(m.year.takeIf { it > 0 }?.let { "${it}년" }, m.grade.takeIf { it > 0 }?.let { "${it}학년" }).joinToString(" / "),
                getString(R.string.exam_detail_period), m.period,
            )
            .pair(
                getString(R.string.exam_detail_subject_dept), listOf(m.subject, m.department).filter { it.isNotBlank() }.joinToString(" / "),
                getString(R.string.exam_detail_size), formatBytes(doc.size),
            )
            .row(getString(R.string.exam_detail_cutoffs), m.cutoffs, R.color.krds_badge_purple_fg)
            .row(getString(R.string.exam_detail_average), m.average.takeIf { it.isNotBlank() }?.let { "${it}점" }, R.color.krds_primary_text)
            .infoBox(getString(R.string.exam_detail_desc), listOf(doc.notes, doc.tags.joinToString(", ") { "#$it" }).filter { it.isNotBlank() }.joinToString("\n"))

        val extras = buildList {
            if (doc.hasAnswerSheet) add(DetailSheet.Action(getString(R.string.exam_answer_sheet), DetailSheet.Style.TERTIARY, R.drawable.ic_file) { showAnswerSheet(doc) })
            if (m.cutoffs.isNotBlank() || m.average.isNotBlank()) add(DetailSheet.Action(getString(R.string.exam_cutoff_table), DetailSheet.Style.TERTIARY, R.drawable.ic_grade) { showCutoffs(doc) })
        }
        if (extras.isNotEmpty()) sheet.actions(*extras.toTypedArray())

        // "정답지 포함 (바로보기·다운로드·극플드라이브)"
        val include = CheckBox(this).apply {
            text = getString(R.string.exam_include_answers)
            isChecked = true
            visibility = if (doc.hasAnswerSheet) View.VISIBLE else View.GONE
        }
        sheet.view(include)
        val withAnswers = { !doc.hasAnswerSheet || include.isChecked }

        sheet.actions(
            DetailSheet.Action(getString(R.string.exam_add_drive), DetailSheet.Style.SECONDARY, R.drawable.ic_drive) { btn -> addToDrive(doc, withAnswers(), btn) },
            DetailSheet.Action(getString(R.string.library_view), DetailSheet.Style.SECONDARY, R.drawable.ic_visibility) {
                startActivity(PdfViewerActivity.intent(this, doc.filename, doc.title, withAnswers()))
            },
        )
        sheet.actions(
            DetailSheet.Action(getString(R.string.library_download), DetailSheet.Style.PRIMARY, R.drawable.ic_download) {
                kr.co.gcflarchive.app.web.GcflWebView.download(
                    this, LibraryRepository.downloadUrl(doc.filename, withAnswers()),
                    fileName = doc.title + ".pdf", mimeType = "application/pdf",
                )
            },
        )
        sheet.show()
    }

    private fun addToDrive(doc: LibraryDocument, includeAnswers: Boolean, button: MaterialButton) {
        button.isEnabled = false
        lifecycleScope.launch {
            runCatching { LibraryRepository.addExamToDrive(doc.filename, includeAnswers) }
                .onSuccess { msg ->
                    Toast.makeText(this@PastExamsActivity, getString(R.string.exam_drive_added, msg), Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    if (e is LoginRequiredException) requestLogin()
                    else Toast.makeText(this@PastExamsActivity, e.message ?: getString(R.string.exam_drive_failed), Toast.LENGTH_SHORT).show()
                }
            button.isEnabled = true
        }
    }

    private fun showAnswerSheet(doc: LibraryDocument) {
        lifecycleScope.launch {
            runCatching { LibraryRepository.answerSheet(doc.filename) }
                .onSuccess { a ->
                    val body = buildList {
                        if (a.answers.isNotBlank()) add(a.answers)
                        a.essays.forEachIndexed { i, t -> if (t.isNotBlank()) add("서술형${i + 1}: $t") }
                    }.joinToString("\n").ifBlank { getString(R.string.exam_answer_empty) }
                    DetailSheet(this@PastExamsActivity, getString(R.string.exam_answer_heading), a.title.ifBlank { doc.title })
                        .pair(getString(R.string.exam_answer_subject), a.subject, getString(R.string.exam_answer_year), a.year)
                        .pair(getString(R.string.exam_answer_term), a.term, getString(R.string.exam_answer_teachers), a.teachers.joinToString(", "))
                        .infoBox(getString(R.string.exam_answer_content), body)
                        .infoBox(getString(R.string.exam_answer_notes), a.notes)
                        .show()
                }
                .onFailure { e ->
                    if (e is LoginRequiredException) requestLogin()
                    else Toast.makeText(this@PastExamsActivity, e.message ?: getString(R.string.exam_answer_failed), Toast.LENGTH_SHORT).show()
                }
        }
    }

    /** 등급컷 상세정보 table (.cutoffs-table). */
    private fun showCutoffs(doc: LibraryDocument) {
        val m = doc.examMeta
        val table = TableLayout(this).apply {
            setBackgroundResource(R.drawable.bg_krds_card)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isStretchAllColumns = true
        }
        fun row(a: String, b: String, header: Boolean = false) {
            val r = TableRow(this)
            listOf(a, b).forEach { text ->
                r.addView(TextView(this).apply {
                    this.text = text
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setTextAppearance(if (header) R.style.TextAppearance_Krds_DetailLabel else R.style.TextAppearance_Krds_DetailValue)
                })
            }
            table.addView(r)
        }
        row(getString(R.string.exam_cutoff_grade), getString(R.string.exam_cutoff_score), header = true)
        val rows = parseCutoffs(m.cutoffs)
        if (rows.isEmpty()) row(getString(R.string.exam_cutoff_empty), "") else rows.forEach { (g, s) -> row(g, s) }
        val sheet = DetailSheet(this, getString(R.string.exam_cutoff_heading), doc.title).view(table)
        if (m.average.isNotBlank()) sheet.row(getString(R.string.exam_detail_average), "${m.average}점", R.color.krds_primary_text)
        sheet.show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ── List ─────────────────────────────────────────────────────────────

    private inner class ExamAdapter : RecyclerView.Adapter<ExamHolder>() {
        private var items: List<LibraryDocument> = emptyList()

        @Suppress("NotifyDataSetChanged") // client-side filter results replace the list
        fun submit(list: List<LibraryDocument>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ExamHolder(ItemExamBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ExamHolder, position: Int) = holder.bind(items[position])
    }

    private inner class ExamHolder(val b: ItemExamBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(doc: LibraryDocument) {
            val m = doc.examMeta
            b.yearGrade.text = listOfNotNull(m.year.takeIf { it > 0 }?.let { "${it}년" }, m.grade.takeIf { it > 0 }?.let { "${it}학년" }).joinToString(" ")
            setBadges(
                b.badges,
                listOf(
                    m.period to Badge.NEUTRAL,
                    m.department to Badge.SUCCESS,
                    (if (doc.hasAnswerSheet) getString(R.string.exam_badge_answer) else "") to Badge.INFO,
                    (if (m.cutoffs.isNotBlank() || m.average.isNotBlank()) getString(R.string.exam_badge_cutoffs) else "") to Badge.PURPLE,
                ),
            )
            b.title.text = doc.title
            b.sub.text = listOf(m.subject.ifBlank { getString(R.string.exam_page_title_short) }, formatBytes(doc.size)).joinToString(" · ")
            val favs = favorites
            b.star.visibility = if (favs != null) View.VISIBLE else View.GONE
            val starred = favs?.contains(doc.filename) == true
            b.star.setIconResource(if (starred) R.drawable.ic_star else R.drawable.ic_star_outline)
            b.star.contentDescription = getString(if (starred) R.string.exam_favorite_remove else R.string.exam_favorite_add)
            b.star.setOnClickListener { toggleFavorite(doc) }
            b.root.setOnClickListener { openDetail(doc) }
        }
    }
}
