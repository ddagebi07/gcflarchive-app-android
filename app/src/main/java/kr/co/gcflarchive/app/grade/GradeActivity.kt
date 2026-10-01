package kr.co.gcflarchive.app.grade

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.data.AuthApi
import kr.co.gcflarchive.app.data.LoginRequiredException
import kr.co.gcflarchive.app.databinding.ActivityGradeBinding
import kr.co.gcflarchive.app.databinding.DialogInputBinding
import kr.co.gcflarchive.app.databinding.ItemGradeBarBinding
import kr.co.gcflarchive.app.databinding.ItemGradeInputBinding
import kr.co.gcflarchive.app.databinding.ItemGradeScoreBinding
import kr.co.gcflarchive.app.databinding.ItemMetaTileBinding
import kr.co.gcflarchive.app.meal.MealRepository
import kr.co.gcflarchive.app.util.Links
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 등급컷 계산기 (grade-calculator.html), native: access choice (학번 / 익명 / 코드),
 * score entry with 틀린 문항, my scores, grade cutoffs and per-question 오답률.
 */
class GradeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGradeBinding

    private var access: GradeAccess = GradeAccess.None
    private var policy: GradePolicy? = null
    private var profile: GradeProfile? = null
    private var statsMode = GradeLogic.STATS_ALL
    private var cutoffSubject: String? = null
    private var wrongSubject: String? = null
    private var wrongSort = WrongRateSort.QUESTION_ASC
    private var guestCode: String? = null
    private val rows = mutableListOf<RowState>()

    /** Migration to 학번 runs right after the login it asked for. */
    private var migrateAfterLogin = false

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK && migrateAfterLogin) {
            migrateAfterLogin = false
            migrate()
        } else {
            load()
        }
    }

    private val prefs by lazy { getSharedPreferences("grade", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGradeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener { finish() }

        setUpAccessCard()
        binding.consentCheck.setOnCheckedChangeListener { _, checked -> binding.btnConsent.isEnabled = checked }
        binding.btnConsent.setOnClickListener { giveScoreConsent() }
        binding.btnSubmitAll.setOnClickListener { submitAll() }
        binding.btnReselect.setOnClickListener {
            lifecycleScope.launch {
                GradeRepository.clearGuest()
                load()
            }
        }
        binding.btnMigrate.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.grade_migrate_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    migrateAfterLogin = true
                    login.launch(LoginActivity.intent(this))
                }
                .show()
        }
        binding.statsModeGroup.check(R.id.statsAll)
        binding.statsModeGroup.addOnButtonCheckedListener { group, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val mode = if (id == R.id.statsVerified) GradeLogic.STATS_VERIFIED else GradeLogic.STATS_ALL
            if (mode == GradeLogic.STATS_VERIFIED && access !is GradeAccess.Verified) {
                group.check(R.id.statsAll)
                snack(getString(R.string.grade_guest_verified_only))
                return@addOnButtonCheckedListener
            }
            if (mode != statsMode) {
                statsMode = mode
                loadCutoff()
                loadWrongRates()
            }
        }
        for ((sort, label) in listOf(
            WrongRateSort.QUESTION_ASC to R.string.grade_sort_q_asc,
            WrongRateSort.QUESTION_DESC to R.string.grade_sort_q_desc,
            WrongRateSort.RATE_DESC to R.string.grade_sort_rate_desc,
            WrongRateSort.RATE_ASC to R.string.grade_sort_rate_asc,
        )) {
            val chip = filterChip(binding.wrongSort, getString(label))
            chip.isChecked = sort == wrongSort
            chip.setOnClickListener {
                wrongSort = sort
                loadWrongRates()
            }
        }
        binding.swipe.setOnRefreshListener { load() }
        load()
    }

    // ── access ────────────────────────────────────────────────────────────

    private fun setUpAccessCard() {
        for (g in listOf("1", "2", "3")) filterChip(binding.guestGradeGroup, getString(R.string.grade_grade_n, g)).tag = g
        filterChip(binding.guestTrackGroup, getString(R.string.grade_track_en)).tag = "영어과"
        filterChip(binding.guestTrackGroup, getString(R.string.grade_track_non_en)).tag = "비영어과"
        binding.btnStartGuest.setOnClickListener { startGuest() }
        binding.btnVerify.setOnClickListener { login.launch(LoginActivity.intent(this)) }
        binding.btnCodeLogin.setOnClickListener { codeLogin() }
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        binding.errorText.isVisible = false
        lifecycleScope.launch {
            try {
                access = GradeRepository.access()
                when (access) {
                    GradeAccess.None -> showOnly(binding.accessCard)
                    GradeAccess.NeedsScoreConsent -> showOnly(binding.consentCard)
                    else -> enterCalculator()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ScoreConsentRequiredException) {
                showOnly(binding.consentCard)
            } catch (e: LoginRequiredException) {
                showOnly(binding.accessCard)
            } catch (e: Exception) {
                binding.errorText.isVisible = true
                binding.errorText.text = e.message ?: getString(R.string.grade_error)
            } finally {
                binding.swipe.isRefreshing = false
            }
        }
    }

    private fun showOnly(view: View?) {
        binding.accessCard.isVisible = view == binding.accessCard
        binding.consentCard.isVisible = view == binding.consentCard
        binding.mainGroup.isVisible = view == binding.mainGroup
    }

    private fun startGuest() {
        val grade = binding.guestGradeGroup.checkedChip()?.tag as? String
        val track = binding.guestTrackGroup.checkedChip()?.tag as? String
        if (grade == null || track == null) {
            binding.accessStatus.setText(R.string.grade_pick_both)
            return
        }
        lifecycleScope.launch {
            runCatching { GradeRepository.startGuest(grade, track) }
                .onSuccess {
                    saveGuestCode(null)
                    load()
                }
                .onFailure { e -> if (e is CancellationException) throw e else binding.accessStatus.text = e.message }
        }
    }

    private fun codeLogin() {
        val field = DialogInputBinding.inflate(layoutInflater)
        field.inputLayout.hint = getString(R.string.grade_code_hint)
        field.input.inputType = InputType.TYPE_CLASS_NUMBER
        field.input.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(4))
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.grade_code_title)
            .setView(field.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val code = field.input.text?.toString().orEmpty()
            if (code.length != 4) {
                field.inputLayout.error = getString(R.string.grade_code_invalid)
                return@setOnClickListener
            }
            lifecycleScope.launch {
                runCatching { GradeRepository.guestLogin(code) }
                    .onSuccess {
                        saveGuestCode(code)
                        dialog.dismiss()
                        load()
                    }
                    .onFailure { e -> if (e is CancellationException) throw e else field.inputLayout.error = e.message }
            }
        }
    }

    private fun giveScoreConsent() {
        binding.btnConsent.isEnabled = false
        lifecycleScope.launch {
            runCatching { AuthApi.consentStudent(scoreConsent = true) }
                .onSuccess { load() }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    binding.btnConsent.isEnabled = true
                    snack(e.message ?: getString(R.string.grade_error))
                }
        }
    }

    private fun migrate() {
        lifecycleScope.launch {
            runCatching { GradeRepository.migrateGuestScores() }
                .onSuccess { r ->
                    saveGuestCode(null)
                    snack(
                        if (r.moved > 0) "익명 점수 ${r.moved}건을 학번 계정으로 이전했습니다." + (if (r.skipped > 0) " (중복 ${r.skipped}건 제외)" else "")
                        else "이전할 익명 점수가 없습니다.",
                    )
                }
                .onFailure { e -> if (e is CancellationException) throw e else snack(e.message ?: "익명 점수를 학번 계정으로 이전하지 못했습니다.") }
            load()
        }
    }

    // ── calculator ────────────────────────────────────────────────────────

    private suspend fun enterCalculator() {
        policy = GradeRepository.policy()
        refreshProfile()
        showOnly(binding.mainGroup)
        val verified = access is GradeAccess.Verified
        if (!verified && statsMode == GradeLogic.STATS_VERIFIED) {
            statsMode = GradeLogic.STATS_ALL
            binding.statsModeGroup.check(R.id.statsAll)
        }
        loadCutoff()
        loadWrongRates()
    }

    private suspend fun refreshProfile() {
        val prof = GradeRepository.profile(policy)
        profile = prof
        policy = prof.policy
        renderIdentity(prof)
        val testUser = isTestUser()
        binding.inputSection.isVisible = !testUser
        binding.mineSection.isVisible = !testUser
        renderInputRows(prof)
        renderMine(prof)
        renderSubjectChips(prof)
    }

    private fun isTestUser() = GradeLogic.isTestUser((access as? GradeAccess.Verified)?.userId)
    private fun isAdmin() = (access as? GradeAccess.Verified)?.isAdmin == true

    private fun renderIdentity(prof: GradeProfile) {
        val exam = prof.policy.examName.ifBlank { getString(R.string.grade_exam_unset) }
        binding.guestRow.isVisible = access is GradeAccess.Guest
        binding.identity.text = when (val a = access) {
            is GradeAccess.Verified -> getString(R.string.grade_identity_verified, a.userId, exam)
            is GradeAccess.Guest -> guestCode()?.let { getString(R.string.grade_identity_guest, a.displayName, it, exam) }
                ?: getString(R.string.grade_identity_guest_nocode, a.displayName, exam)
            else -> exam
        }
    }

    private fun renderInputRows(prof: GradeProfile) {
        val p = prof.policy
        binding.inputRows.removeAllViews()
        rows.clear()
        val inputRows = GradeLogic.inputRows(p)
        if (inputRows.isEmpty()) {
            binding.inputRows.addView(TextView(this).apply {
                setText(R.string.grade_no_subjects)
                setTextAppearance(R.style.TextAppearance_Krds_Meta)
                setPadding(0, dp(12), 0, dp(12))
            })
        }
        for (row in inputRows) {
            val v = ItemGradeInputBinding.inflate(layoutInflater, binding.inputRows, false)
            val initial = when (row) {
                is InputRow.Fixed -> row.subject
                is InputRow.Group -> row.group.subjects.firstOrNull { prof.current.containsKey(it) }
            }
            val state = RowState(row, v, initial)
            rows += state
            when (row) {
                is InputRow.Fixed -> v.subject.text = row.subject
                is InputRow.Group -> {
                    v.subject.isVisible = false
                    v.subjectPicker.isVisible = true
                    v.subjectPicker.setOnClickListener {
                        val menu = PopupMenu(this, v.subjectPicker)
                        row.group.subjects.forEachIndexed { i, s -> menu.menu.add(0, i, i, s) }
                        menu.setOnMenuItemClickListener { item ->
                            state.subject = row.group.subjects[item.itemId]
                            state.wrong.clear()
                            prof.current[state.subject]?.let { state.wrong += it.wrongQuestions }
                            v.score.setText(prof.current[state.subject]?.score?.let(GradeLogic::formatScore))
                            bindRow(state)
                            true
                        }
                        menu.show()
                    }
                }
            }
            state.subject?.let { s -> prof.current[s]?.let { cur ->
                v.score.setText(GradeLogic.formatScore(cur.score))
                state.wrong += cur.wrongQuestions
            } }
            v.wrongToggle.setOnClickListener {
                v.wrongGroup.isVisible = !v.wrongGroup.isVisible
                if (v.wrongGroup.isVisible) bindWrongChips(state)
            }
            v.btnSubmit.setOnClickListener { runAction(state, Action.SUBMIT) }
            v.btnUpdate.setOnClickListener { runAction(state, Action.UPDATE) }
            v.btnDelete.setOnClickListener { runAction(state, Action.DELETE) }
            bindRow(state)
            binding.inputRows.addView(v.root)
        }
    }

    private fun bindRow(state: RowState) {
        val p = policy ?: return
        val v = state.view
        val subject = state.subject
        if (state.row is InputRow.Group) {
            val g = (state.row as InputRow.Group).group
            v.subjectPicker.text = subject ?: getString(R.string.grade_pick_subject, g.label.ifBlank { g.slotSubject.ifBlank { "선택 과목" } })
        }
        val hasCurrent = subject != null && profile?.current?.containsKey(subject) == true
        val open = GradeLogic.openInfo(state.row.scheduleSubject.ifBlank { subject.orEmpty() }, p)
        v.windowHint.isVisible = subject != null && !open.open && open.availableAt != null
        v.windowHint.text = open.availableAt?.let { getString(R.string.grade_available_at, kst(it)) }
        v.score.isEnabled = subject != null && open.open
        v.btnSubmit.isEnabled = subject != null && !hasCurrent && open.open
        v.btnUpdate.isEnabled = hasCurrent
        v.btnDelete.isEnabled = hasCurrent
        val count = subject?.let { GradeLogic.questionCount(it, p) } ?: 0
        v.wrongToggle.isVisible = subject != null && count > 0
        v.wrongToggle.text = if (state.wrong.isEmpty()) getString(R.string.grade_wrong_toggle) else getString(R.string.grade_wrong_toggle_n, state.wrong.size)
        if (v.wrongGroup.isVisible) bindWrongChips(state)
    }

    private fun bindWrongChips(state: RowState) {
        val p = policy ?: return
        val subject = state.subject ?: return
        val group = state.view.wrongGroup
        group.removeAllViews()
        for (n in 1..GradeLogic.questionCount(subject, p)) {
            val chip = filterChip(group, GradeLogic.questionLabel(subject, n, p))
            chip.isChecked = n in state.wrong
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) state.wrong += n else state.wrong -= n
                state.view.wrongToggle.text = if (state.wrong.isEmpty()) getString(R.string.grade_wrong_toggle)
                else getString(R.string.grade_wrong_toggle_n, state.wrong.size)
            }
        }
    }

    private enum class Action { SUBMIT, UPDATE, DELETE }

    private fun runAction(state: RowState, action: Action) {
        val subject = state.subject ?: return
        if (policy?.examName.isNullOrBlank()) return snack(getString(R.string.grade_exam_missing))
        if (action == Action.DELETE) {
            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.grade_delete_confirm, subject))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.grade_delete) { _, _ -> perform(state, action, subject) }
                .show()
            return
        }
        if (action == Action.SUBMIT && access is GradeAccess.Guest && guestCode() == null) {
            confirmGuestSubmit { perform(state, action, subject) }
            return
        }
        perform(state, action, subject)
    }

    private fun perform(state: RowState, action: Action, subject: String) {
        lifecycleScope.launch {
            try {
                when (action) {
                    Action.SUBMIT -> {
                        val score = GradeLogic.parseScore(state.view.score.text.toString()).getOrThrow()
                        GradeRepository.submit(subject, score, state.wrong.sorted()).guestAccessCode?.let(::showIssuedCode)
                        binding.submitStatus.text = getString(R.string.grade_done_submit, subject)
                    }
                    Action.UPDATE -> {
                        val score = GradeLogic.parseScore(state.view.score.text.toString()).getOrThrow()
                        GradeRepository.update(subject, score, state.wrong.sorted())
                        binding.submitStatus.text = getString(R.string.grade_done_update, subject)
                    }
                    Action.DELETE -> {
                        GradeRepository.delete(subject)
                        binding.submitStatus.text = getString(R.string.grade_done_delete, subject)
                    }
                }
                refreshProfile()
                loadCutoff()
                loadWrongRates()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snack(e.message ?: getString(R.string.grade_error))
            }
        }
    }

    private fun submitAll() {
        val p = policy ?: return
        if (p.examName.isBlank()) return snack(getString(R.string.grade_exam_missing))
        val targets = rows.filter { s ->
            val subject = s.subject ?: return@filter false
            profile?.current?.containsKey(subject) != true &&
                GradeLogic.openInfo(s.row.scheduleSubject.ifBlank { subject }, p).open &&
                s.view.score.text?.isNotBlank() == true
        }
        if (targets.isEmpty()) return snack(getString(R.string.grade_nothing_to_submit))
        val go = {
            lifecycleScope.launch {
                binding.submitStatus.text = getString(R.string.grade_submitting, targets.size)
                val failures = mutableListOf<String>()
                for (s in targets) {
                    val subject = s.subject!!
                    try {
                        val score = GradeLogic.parseScore(s.view.score.text.toString()).getOrThrow()
                        GradeRepository.submit(subject, score, s.wrong.sorted()).guestAccessCode?.let(::showIssuedCode)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures += "$subject: ${e.message ?: "제출 실패"}"
                    }
                }
                val ok = targets.size - failures.size
                binding.submitStatus.text = if (failures.isEmpty()) getString(R.string.grade_submitted_all, ok)
                else getString(R.string.grade_submitted_partial, ok, targets.size, failures.joinToString(" | "))
                runCatching { refreshProfile() }
                loadCutoff()
                loadWrongRates()
            }
        }
        if (access is GradeAccess.Guest && guestCode() == null) confirmGuestSubmit { go() } else go()
    }

    private fun confirmGuestSubmit(onOk: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.grade_guest_submit_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.grade_submit) { _, _ -> onOk() }
            .show()
    }

    /** The server shows the 4-digit code only once; keep it on the device and show it big. */
    private fun showIssuedCode(code: String) {
        if (code.length != 4 || code == guestCode()) return
        saveGuestCode(code)
        profile?.let(::renderIdentity)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.grade_code_issued_title, code))
            .setMessage(R.string.grade_code_issued_desc)
            .setPositiveButton(R.string.grade_copy_code) { _, _ -> Links.copy(this, getString(R.string.grade_title), code) }
            .setNegativeButton(R.string.confirm, null)
            .setCancelable(false)
            .show()
    }

    private fun guestCode(): String? = guestCode ?: prefs.getString(KEY_CODE, null)?.takeIf { it.length == 4 }

    private fun saveGuestCode(code: String?) {
        guestCode = code
        prefs.edit().apply { if (code == null) remove(KEY_CODE) else putString(KEY_CODE, code) }.apply()
    }

    private fun renderMine(prof: GradeProfile) {
        binding.mineRows.removeAllViews()
        if (prof.scores.isEmpty()) {
            binding.mineRows.addView(metaText(getString(R.string.grade_mine_empty)))
            return
        }
        for (s in prof.scores) {
            val v = ItemGradeScoreBinding.inflate(layoutInflater, binding.mineRows, false)
            v.title.text = s.subject
            val parts = mutableListOf(s.examName)
            if (s.percentile != "-") parts += getString(R.string.grade_percentile, s.percentile)
            GradeLogic.parseInstant(s.timestamp)?.let { parts += kst(it) }
            v.meta.text = parts.joinToString(" · ")
            v.score.text = getString(R.string.grade_score_value, s.score)
            v.grade.isVisible = s.grade != null
            s.grade?.let { styleGradeChip(v.grade, it) }
            binding.mineRows.addView(v.root)
        }
    }

    private fun renderSubjectChips(prof: GradeProfile) {
        val subjects = GradeLogic.statsSubjects(prof, isTestUser(), isAdmin())
        cutoffSubject = cutoffSubject?.takeIf { it in subjects } ?: subjects.firstOrNull()
        wrongSubject = wrongSubject?.takeIf { it in subjects } ?: subjects.firstOrNull()
        fillSubjectChips(binding.cutoffSubjects, subjects, cutoffSubject) { cutoffSubject = it; loadCutoff() }
        fillSubjectChips(binding.wrongSubjects, subjects, wrongSubject) { wrongSubject = it; loadWrongRates() }
    }

    private fun fillSubjectChips(group: ChipGroup, subjects: List<String>, selected: String?, onPick: (String) -> Unit) {
        group.removeAllViews()
        for (s in subjects) {
            val chip = filterChip(group, s)
            chip.isChecked = s == selected
            chip.setOnClickListener { onPick(s) }
        }
    }

    private fun loadCutoff() {
        val subject = cutoffSubject
        binding.cutoffMeta.removeAllViews()
        binding.cutoffRows.removeAllViews()
        if (subject == null) {
            binding.cutoffStatus.setText(R.string.grade_no_stats_subjects)
            return
        }
        binding.cutoffStatus.setText(R.string.grade_loading)
        lifecycleScope.launch {
            val result = runCatching { GradeRepository.cutoff(subject, statsMode) }
            if (subject != cutoffSubject) return@launch
            result.onFailure { e ->
                if (e is CancellationException) throw e
                binding.cutoffStatus.text = e.message
            }.onSuccess { r ->
                val mine = profile?.scores?.firstOrNull { it.subject == subject }?.score?.toDoubleOrNull()
                metaTiles(binding.cutoffMeta, listOfNotNull(
                    getString(R.string.grade_meta_samples) to getString(R.string.grade_people, r.sampleCount),
                    getString(R.string.grade_meta_average) to getString(R.string.grade_points, String.format(Locale.ROOT, "%.2f", r.averageScore)),
                    mine?.let { getString(R.string.grade_meta_mine) to getString(R.string.grade_points, GradeLogic.formatScore(it)) },
                ))
                if (!r.visible) {
                    val parts = mutableListOf(getString(R.string.grade_cutoff_hidden))
                    if (r.requiredSampleCount > r.sampleCount) parts += getString(R.string.grade_cutoff_need, r.requiredSampleCount - r.sampleCount)
                    GradeLogic.parseInstant(r.publicationTime)?.let { parts += getString(R.string.grade_cutoff_release, kst(it)) }
                    binding.cutoffStatus.text = parts.joinToString(" | ")
                    return@onSuccess
                }
                binding.cutoffStatus.setText(if (r.rows.isEmpty()) R.string.grade_no_data else R.string.grade_cutoff_public)
                val myGrade = GradeLogic.myGrade(r.rows, mine)
                for (row in r.rows) {
                    val v = ItemGradeBarBinding.inflate(layoutInflater, binding.cutoffRows, false)
                    styleGradeChip(v.label, row.grade)
                    v.value.text = row.score?.let { getString(R.string.grade_points, String.format(Locale.ROOT, "%.1f", it)) }
                        ?: getString(R.string.grade_cutoff_na)
                    v.bar.setProgressCompat(row.score?.toInt()?.coerceIn(0, 100) ?: 0, false)
                    val isMine = myGrade == row.grade
                    v.note.isVisible = isMine
                    if (isMine) {
                        v.note.text = getString(R.string.grade_cutoff_mine, GradeLogic.formatScore(mine!!))
                        v.note.setTextColor(getColor(R.color.krds_primary_text))
                        v.root.setBackgroundColor(getColor(R.color.krds_primary_tint))
                    }
                    binding.cutoffRows.addView(v.root)
                }
            }
        }
    }

    private fun loadWrongRates() {
        val subject = wrongSubject
        binding.wrongMeta.removeAllViews()
        binding.wrongRows.removeAllViews()
        if (subject == null) {
            binding.wrongStatus.setText(R.string.grade_no_stats_subjects)
            return
        }
        binding.wrongStatus.setText(R.string.grade_loading)
        lifecycleScope.launch {
            val result = runCatching { GradeRepository.wrongRates(subject, statsMode) }
            if (subject != wrongSubject) return@launch
            result.onFailure { e ->
                if (e is CancellationException) throw e
                binding.wrongStatus.text = e.message
            }.onSuccess { (rates, responses) ->
                metaTiles(binding.wrongMeta, listOf(getString(R.string.grade_meta_responses) to getString(R.string.grade_people, responses)))
                val sorted = GradeLogic.sortWrongRates(rates, wrongSort)
                binding.wrongStatus.text = if (sorted.isEmpty()) getString(R.string.grade_no_data) else ""
                binding.wrongStatus.isVisible = sorted.isEmpty()
                val p = policy
                for (r in sorted) {
                    val v = ItemGradeBarBinding.inflate(layoutInflater, binding.wrongRows, false)
                    val label = p?.let { GradeLogic.questionLabel(subject, r.questionNo, it) } ?: r.questionNo.toString()
                    v.label.text = getString(R.string.grade_question, label)
                    v.label.setBackgroundResource(R.drawable.bg_krds_badge_neutral)
                    v.label.setTextColor(getColor(R.color.krds_badge_neutral_fg))
                    v.value.text = getString(R.string.grade_rate, String.format(Locale.ROOT, "%.2f", r.percentage))
                    v.note.text = getString(R.string.grade_wrong_count, r.wrong, r.responses)
                    v.bar.setProgressCompat(r.percentage.toInt().coerceIn(0, 100), false)
                    val color = when {
                        r.percentage >= 60 -> R.color.krds_danger
                        r.percentage >= 40 -> R.color.krds_star
                        else -> R.color.krds_primary
                    }
                    v.bar.setIndicatorColor(getColor(color))
                    binding.wrongRows.addView(v.root)
                }
            }
        }
    }

    // ── small view helpers ────────────────────────────────────────────────

    private fun filterChip(group: ChipGroup, text: String): Chip =
        (LayoutInflater.from(this).inflate(R.layout.item_krds_filter_chip, group, false) as Chip).also {
            it.id = View.generateViewId()
            it.text = text
            it.isCheckable = true
            group.addView(it)
        }

    private fun ChipGroup.checkedChip(): Chip? = findViewById(checkedChipId)

    private fun metaTiles(host: LinearLayout, items: List<Pair<String, String>>) {
        host.removeAllViews()
        for ((k, value) in items) {
            val t = ItemMetaTileBinding.inflate(layoutInflater, host, false)
            t.key.text = k
            t.value.text = value
            host.addView(t.root)
        }
    }

    private fun styleGradeChip(view: TextView, grade: Int) {
        view.text = getString(R.string.grade_n, grade)
        val (bg, fg) = when {
            grade <= 2 -> R.drawable.bg_krds_badge_info to R.color.krds_badge_info_fg
            grade <= 4 -> R.drawable.bg_krds_badge_success to R.color.krds_badge_success_fg
            grade <= 6 -> R.drawable.bg_krds_badge_neutral to R.color.krds_badge_neutral_fg
            else -> R.drawable.bg_krds_badge_danger to R.color.krds_danger
        }
        view.setBackgroundResource(bg)
        view.setTextColor(getColor(fg))
    }

    private fun metaText(text: String) = TextView(this).apply {
        this.text = text
        setTextAppearance(R.style.TextAppearance_Krds_Meta)
        setPadding(0, dp(12), 0, dp(12))
    }

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun kst(i: Instant): String = i.atZone(MealRepository.SEOUL).format(TIME)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** One input row and what the user picked in it. */
    private class RowState(val row: InputRow, val view: ItemGradeInputBinding, var subject: String?) {
        val wrong = sortedSetOf<Int>()
    }

    companion object {
        private const val KEY_CODE = "guest_access_code"
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

        fun intent(context: Context) = Intent(context, GradeActivity::class.java)
    }
}
