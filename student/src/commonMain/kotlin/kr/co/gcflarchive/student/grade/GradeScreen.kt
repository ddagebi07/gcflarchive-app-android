package kr.co.gcflarchive.student.grade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import kr.co.gcflarchive.core.grade.CutoffResult
import kr.co.gcflarchive.core.grade.GradeAccess
import kr.co.gcflarchive.core.grade.GradeLogic
import kr.co.gcflarchive.core.grade.GradePolicy
import kr.co.gcflarchive.core.grade.GradeProfile
import kr.co.gcflarchive.core.grade.InputRow
import kr.co.gcflarchive.core.grade.ScoreConsentRequiredException
import kr.co.gcflarchive.core.grade.WrongRate
import kr.co.gcflarchive.core.grade.WrongRateSort
import kr.co.gcflarchive.core.meal.MealRepository
import kr.co.gcflarchive.core.net.LoginRequiredException
import kr.co.gcflarchive.core.util.fixed
import kr.co.gcflarchive.student.AppState
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.platform.authenticateUser
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.ChoiceChips
import kr.co.gcflarchive.student.ui.InfoBox
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsChip
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.Loading
import kr.co.gcflarchive.student.ui.MetaTile
import kr.co.gcflarchive.student.ui.PageHeader
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.SecondaryButton
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.userMessage

/** One input row and what the user picked/typed in it. */
@Stable
private class RowState(val row: InputRow, subject: String?, score: String, wrong: Set<Int>) {
    var subject by mutableStateOf(subject)
    var score by mutableStateOf(score)
    var wrong by mutableStateOf(wrong)
    var showWrong by mutableStateOf(false)
}

private sealed interface Phase {
    data object Loading : Phase
    /** [hakbun]: logged in (익명 setup pre-filled from it, and "학번으로 계속" offered). */
    data class Access(val hakbun: String?) : Phase
    data object Consent : Phase
    data class Error(val message: String) : Phase
    data class Ready(val access: GradeAccess) : Phase
}

private fun kst(i: Instant): String = i.toLocalDateTime(MealRepository.SEOUL).let {
    "${it.monthNumber}/${it.dayOfMonth} ${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
}

/**
 * 등급컷 계산기 (grade-calculator.html): access choice (학번 / 익명 / 코드), score entry with
 * 틀린 문항, my scores, grade cutoffs and per-question 오답률.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<Phase>(Phase.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var migratePending by remember { mutableStateOf(false) }

    suspend fun migrate() {
        try {
            val r = app.services.grades.migrateGuestScores()
            app.services.prefs.gradeGuestCode = null
            app.toast(
                if (r.moved > 0) "익명 점수 ${r.moved}건을 학번 계정으로 이전했습니다." + (if (r.skipped > 0) " (중복 ${r.skipped}건 제외)" else "")
                else "이전할 익명 점수가 없습니다.",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.toast(e.userMessage("익명 점수를 학번 계정으로 이전하지 못했습니다."))
        }
    }

    val prefs = app.services.prefs

    LaunchedEffect(reload, app.sessionVersion) {
        refreshing = true
        phase = try {
            var a = app.services.grades.access()
            // "내 학번에 등록": the login it asked for just finished.
            if (migratePending && a is GradeAccess.Verified) {
                migratePending = false
                migrate()
                prefs.gradeAccessMode = MODE_VERIFIED
                a = app.services.grades.access()
            }
            // A logged-in student who chose 익명 goes back into the saved anonymous identity.
            val code = prefs.gradeGuestCode
            val loggedIn = a is GradeAccess.Verified || a is GradeAccess.NeedsScoreConsent
            if (loggedIn && prefs.gradeAccessMode == MODE_GUEST && code != null) {
                val restored = runCatching { app.services.grades.guestLogin(code, keepLogin = true) }
                if (restored.isSuccess) a = app.services.grades.access()
                // 401 = no longer valid (e.g. a new exam started): forget it.
                else if (restored.exceptionOrNull() is LoginRequiredException) prefs.gradeGuestCode = null
            }
            val hakbun = when (a) {
                is GradeAccess.Verified -> a.userId
                is GradeAccess.NeedsScoreConsent -> a.userId
                is GradeAccess.Guest -> a.hakbun
                GradeAccess.None -> null
            }
            when {
                a is GradeAccess.Guest -> Phase.Ready(a)
                // Logged in but no choice yet (or 익명 wanted without a usable saved code).
                hakbun != null && prefs.gradeAccessMode != MODE_VERIFIED -> Phase.Access(hakbun)
                a is GradeAccess.NeedsScoreConsent -> Phase.Consent
                a is GradeAccess.Verified -> Phase.Ready(a)
                else -> Phase.Access(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScoreConsentRequiredException) {
            Phase.Consent
        } catch (e: LoginRequiredException) {
            Phase.Access(null)
        } catch (e: Exception) {
            Phase.Error(e.userMessage("등급컷 정보를 불러오지 못했습니다."))
        }
        refreshing = false
    }
    // Coming back from the login screen re-runs the effect via sessionVersion.

    ScreenScaffold("등급컷 계산기") {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { reload++ }, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageHeader("등급컷 계산기", "과목별 점수 입력 및 실시간 예상 등급컷·오답률 통계")
                when (val p = phase) {
                    Phase.Loading -> Loading()
                    is Phase.Access -> AccessCard(app, p.hakbun, onDone = { reload++ })
                    Phase.Consent -> ConsentCard(app, onDone = { reload++ })
                    is Phase.Error -> StateMessage(p.message, null, "다시 시도", { reload++ })
                    is Phase.Ready -> Calculator(
                        app, p.access, reload,
                        // A new anonymous identity: don't log back into the saved one.
                        onReselect = {
                            val hakbun = (p.access as? GradeAccess.Guest)?.hakbun
                            scope.launch { app.services.grades.clearGuest(); phase = Phase.Access(hakbun) }
                        },
                        onMigrate = {
                            if ((p.access as? GradeAccess.Guest)?.keepsLogin == true) {
                                scope.launch { migrate(); prefs.gradeAccessMode = MODE_VERIFIED; reload++ }
                            } else {
                                migratePending = true
                                app.requireLogin()
                            }
                        },
                        onSwitchMode = {
                            scope.launch {
                                if (p.access is GradeAccess.Guest) {
                                    prefs.gradeAccessMode = MODE_VERIFIED
                                    app.services.grades.clearGuest() // the saved code still brings it back
                                } else {
                                    prefs.gradeAccessMode = MODE_GUEST
                                }
                                reload++
                            }
                        },
                    )
                }
            }
        }
    }
}

private const val MODE_VERIFIED = "verified"
private const val MODE_GUEST = "guest"

@Composable
private fun AccessCard(app: AppState, hakbun: String?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    // 학년·계열 pre-selected from the 학번 of a logged-in student.
    val defaults = remember(hakbun) { GradeLogic.guestDefaults(hakbun) }
    var grade by remember(hakbun) { mutableStateOf(defaults?.first) }
    var track by remember(hakbun) { mutableStateOf(defaults?.second) }
    var status by remember { mutableStateOf<String?>(null) }
    var codeDialog by remember { mutableStateOf(false) }

    KrdsCard {
        Text("접속 방식 선택", style = KrdsText.cardTitle)
        Text("학번 인증 없이 익명으로도 사용할 수 있습니다. 익명 모드에서는 학년/계열만 선택합니다.", style = KrdsText.meta, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Text("학년", style = KrdsText.label)
        ChoiceChips(listOf<Pair<String?, String>>("1" to "1학년", "2" to "2학년", "3" to "3학년"), grade, { grade = it }, Modifier.padding(vertical = 6.dp))
        Text("계열", style = KrdsText.label)
        ChoiceChips(listOf<Pair<String?, String>>("영어과" to "영어과 (1~4반)", "비영어과" to "비영어과 (5~10반)"), track, { track = it }, Modifier.padding(vertical = 6.dp))
        status?.let { Text(it, style = KrdsText.meta.copy(color = Krds.colors.danger)) }
        PrimaryButton("익명으로 계속", {
            val g = grade
            val t = track
            if (g == null || t == null) { status = "학년과 계열을 모두 선택해 주세요."; return@PrimaryButton }
            scope.launch {
                try {
                    app.services.grades.startGuest(g, t, keepLogin = hakbun != null)
                    app.services.prefs.gradeGuestCode = null
                    if (hakbun != null) app.services.prefs.gradeAccessMode = MODE_GUEST
                    onDone()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status = e.userMessage()
                }
            }
        }, Modifier.fillMaxWidth().padding(top = 8.dp))
        SecondaryButton("코드 로그인 (익명 재방문용)", { codeDialog = true }, Modifier.fillMaxWidth().padding(top = 8.dp))
        if (hakbun != null) {
            SecondaryButton("학번($hakbun)으로 계속", {
                app.services.prefs.gradeAccessMode = MODE_VERIFIED
                onDone()
            }, Modifier.fillMaxWidth().padding(top = 8.dp))
        } else {
            SecondaryButton("학번 인증하기", { app.requireLogin() }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        InfoBox(
            if (defaults != null) "학번 기준으로 ${defaults.first}학년 ${defaults.second}를 골라 두었습니다. 익명으로 계속하면 점수가 학번과 연결되지 않으며, 발급되는 접근 코드는 이 기기에 자동으로 저장됩니다."
            else "익명 제출 후 4자리 접근 코드가 발급되어 이 기기에 자동으로 저장됩니다. 같은 시험 기간 동안 다시 접속해 점수 수정/조회가 가능합니다.",
            Modifier.padding(top = 12.dp),
        )
    }

    if (codeDialog) {
        // Never pre-filled: the saved code is only shown after 지문/Face ID.
        var code by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { codeDialog = false },
            title = { Text("접근 코드 입력") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(4); error = null },
                    label = { Text("4자리 코드") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (code.length != 4) { error = "4자리 코드를 입력해 주세요."; return@TextButton }
                    scope.launch {
                        try {
                            app.services.grades.guestLogin(code, keepLogin = hakbun != null)
                            app.services.prefs.gradeGuestCode = code
                            if (hakbun != null) app.services.prefs.gradeAccessMode = MODE_GUEST
                            codeDialog = false
                            onDone()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.userMessage()
                        }
                    }
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { codeDialog = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun ConsentCard(app: AppState, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var checked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    KrdsCard {
        Text("원점수 수집·이용 동의", style = KrdsText.cardTitle)
        Text(
            "등급컷 서비스는 시험 원점수를 수집합니다. 수집한 원점수는 등급컷과 백분위 산출을 위한 자료로만 활용되며, 암호화되어 저장되므로 관리자는 개인의 학번과 점수를 열람할 수 없습니다. 원점수는 당해 평가 종료일로부터 30일 동안 보관됩니다.",
            style = KrdsText.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().clickable { checked = !checked }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, { checked = it }, colors = CheckboxDefaults.colors(checkedColor = Krds.colors.primary))
            Text("[선택] 원점수 수집·이용에 동의합니다.", style = KrdsText.body)
        }
        PrimaryButton("동의하고 계속", {
            busy = true
            scope.launch {
                try {
                    app.services.auth.consentStudent(scoreConsent = true)
                    onDone()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    app.toast(e.userMessage())
                } finally {
                    busy = false
                }
            }
        }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = checked && !busy)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Calculator(
    app: AppState,
    access: GradeAccess,
    reloadKey: Int,
    onReselect: () -> Unit,
    onMigrate: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val prefs = app.services.prefs
    var policy by remember { mutableStateOf<GradePolicy?>(null) }
    var profile by remember { mutableStateOf<GradeProfile?>(null) }
    var rows by remember { mutableStateOf<List<RowState>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitStatus by remember { mutableStateOf<String?>(null) }
    var statsMode by remember { mutableStateOf(GradeLogic.STATS_ALL) }
    var cutoffSubject by remember { mutableStateOf<String?>(null) }
    var wrongSubject by remember { mutableStateOf<String?>(null) }
    var wrongSort by remember { mutableStateOf(WrongRateSort.QUESTION_ASC) }
    var statsVersion by remember { mutableIntStateOf(0) }
    var issuedCode by remember { mutableStateOf<String?>(null) }
    var revealedCode by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    val verified = access is GradeAccess.Verified

    /** The saved anonymous code is shown only after 지문 / Face ID. */
    fun revealCode() {
        val code = prefs.gradeGuestCode ?: return app.toast("이 기기에 저장된 접근 코드가 없습니다.")
        scope.launch {
            if (authenticateUser("익명 접근 코드를 확인합니다.")) revealedCode = code
            else app.toast("본인 확인을 하지 못했습니다. 기기에 지문(Face ID)이나 암호가 설정되어 있는지 확인해 주세요.")
        }
    }
    val testUser = GradeLogic.isTestUser((access as? GradeAccess.Verified)?.userId)
    val isAdmin = (access as? GradeAccess.Verified)?.isAdmin == true

    suspend fun refreshProfile() {
        val prof = app.services.grades.profile(policy)
        profile = prof
        policy = prof.policy
        rows = GradeLogic.inputRows(prof.policy).map { row ->
            val subject = when (row) {
                is InputRow.Fixed -> row.subject
                is InputRow.Group -> row.group.subjects.firstOrNull { prof.current.containsKey(it) }
            }
            val cur = subject?.let { prof.current[it] }
            RowState(row, subject, cur?.score?.let(GradeLogic::formatScore).orEmpty(), cur?.wrongQuestions?.toSet().orEmpty())
        }
        val subjects = GradeLogic.statsSubjects(prof, testUser, isAdmin)
        cutoffSubject = cutoffSubject?.takeIf { it in subjects } ?: subjects.firstOrNull()
        wrongSubject = wrongSubject?.takeIf { it in subjects } ?: subjects.firstOrNull()
        statsVersion++
    }

    LaunchedEffect(access, reloadKey) {
        error = null
        try {
            policy = app.services.grades.policy()
            refreshProfile()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage("등급컷 정보를 불러오지 못했습니다.")
        }
    }

    fun issued(code: String?) {
        if (code == null || code.length != 4 || code == prefs.gradeGuestCode) return
        prefs.gradeGuestCode = code
        issuedCode = code
    }

    fun guarded(needsGuestConfirm: Boolean, action: () -> Unit) {
        if (needsGuestConfirm && access is GradeAccess.Guest && prefs.gradeGuestCode == null) {
            confirm = "익명으로 제출하면 4자리 접근 코드가 발급됩니다. 코드를 저장하면 이번 시험 기간 동안 다시 접속해 점수 수정/조회가 가능합니다. 제출하시겠습니까?" to action
        } else action()
    }

    fun perform(state: RowState, action: String) {
        val subject = state.subject ?: return
        if (policy?.examName.isNullOrBlank()) return app.toast("현재 시험명이 아직 설정되지 않았습니다. 관리자에게 문의하세요.")
        scope.launch {
            try {
                when (action) {
                    "submit" -> {
                        val score = GradeLogic.parseScore(state.score).getOrThrow()
                        issued(app.services.grades.submit(subject, score, state.wrong.sorted()).guestAccessCode)
                        submitStatus = "$subject 성적 제출 완료"
                    }
                    "update" -> {
                        val score = GradeLogic.parseScore(state.score).getOrThrow()
                        app.services.grades.update(subject, score, state.wrong.sorted())
                        submitStatus = "$subject 성적 수정 완료"
                    }
                    else -> {
                        app.services.grades.delete(subject)
                        submitStatus = "$subject 성적 삭제 완료"
                    }
                }
                refreshProfile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.toast(e.message ?: "처리하지 못했습니다.")
            }
        }
    }

    fun submitAll() {
        val p = policy ?: return
        if (p.examName.isBlank()) return app.toast("현재 시험명이 아직 설정되지 않았습니다. 관리자에게 문의하세요.")
        val targets = rows.filter { s ->
            val subject = s.subject ?: return@filter false
            profile?.current?.containsKey(subject) != true &&
                GradeLogic.openInfo(s.row.scheduleSubject.ifBlank { subject }, p).open && s.score.isNotBlank()
        }
        if (targets.isEmpty()) return app.toast("제출 가능한 과목의 점수를 하나 이상 입력하세요.")
        guarded(true) {
            scope.launch {
                submitStatus = "${targets.size}개 과목 제출 중…"
                val failures = mutableListOf<String>()
                for (s in targets) {
                    val subject = s.subject ?: continue
                    try {
                        val score = GradeLogic.parseScore(s.score).getOrThrow()
                        issued(app.services.grades.submit(subject, score, s.wrong.sorted()).guestAccessCode)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures += "$subject: ${e.message ?: "제출 실패"}"
                    }
                }
                val ok = targets.size - failures.size
                submitStatus = if (failures.isEmpty()) "${ok}개 과목 성적 제출 완료" else "일부 제출 완료($ok/${targets.size}) - ${failures.joinToString(" | ")}"
                runCatching { refreshProfile() }
            }
        }
    }

    val p = policy
    val prof = profile
    if (error != null) return StateMessage(error!!, null, null)
    if (p == null || prof == null) return Loading()

    // Identity line
    val exam = p.examName.ifBlank { "시험 미설정" }
    KrdsCard {
        Text(
            when (access) {
                is GradeAccess.Verified -> "${access.userId} · $exam"
                is GradeAccess.Guest -> if (prefs.gradeGuestCode != null) "${access.displayName} · 코드 저장됨 · $exam" else "${access.displayName} · 코드 미발급 · $exam"
                else -> exam
            },
            style = KrdsText.cardTitle,
        )
        if (access is GradeAccess.Guest) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("학년/계열 다시 선택", onReselect, Modifier.weight(1f))
                SecondaryButton("내 학번에 등록", {
                    confirm = (if (access.keepsLogin) "익명으로 입력한 점수를 지금 로그인한 학번 계정으로 이전하시겠습니까?"
                    else "익명으로 입력한 점수를 학번 계정으로 이전하시겠습니까? 학번 인증 후 바로 이전됩니다.") to onMigrate
                }, Modifier.weight(1f))
            }
            if (prefs.gradeGuestCode != null) {
                TextButton(onClick = ::revealCode, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.Lock, null, tint = Krds.colors.primaryText)
                    Text(" 접근 코드 보기", color = Krds.colors.primaryText)
                }
            }
        }
        val loggedIn = access is GradeAccess.Verified || (access is GradeAccess.Guest && access.keepsLogin)
        if (loggedIn && !testUser) {
            TextButton(onClick = onSwitchMode, modifier = Modifier.padding(top = 4.dp)) {
                Text(if (access is GradeAccess.Guest) "학번으로 이용하기" else "익명으로 이용하기", color = Krds.colors.primaryText)
            }
        }
    }

    if (!testUser) {
        Section("1", "원점수 입력")
        InfoBox("관리자가 설정한 현재 시험에 대해서만 제출/수정/삭제할 수 있습니다.")
        if (rows.isEmpty()) Text("허용된 과목이 없습니다. 관리자에게 문의하세요.", style = KrdsText.meta)
        rows.forEach { s ->
            InputCard(s, p, prof, onAction = { action ->
                val subject = s.subject ?: return@InputCard
                when (action) {
                    "delete" -> confirm = "$subject 과목의 현재 시험 점수를 삭제하시겠습니까?" to { perform(s, action) }
                    "submit" -> guarded(true) { perform(s, action) }
                    else -> perform(s, action)
                }
            })
        }
        if (rows.isNotEmpty()) PrimaryButton("입력된 점수 전체 제출", ::submitAll, Modifier.fillMaxWidth())
        submitStatus?.let { Text(it, style = KrdsText.meta.copy(color = Krds.colors.primaryText)) }

        Section("2", "내 점수")
        Text("현재까지 제출한 점수입니다. 표본 수가 적은 경우 등급이 부정확하게 산출될 수 있습니다.", style = KrdsText.meta)
        if (prof.scores.isEmpty()) Text("아직 제출한 성적이 없습니다.", style = KrdsText.meta.copy(color = Krds.colors.textDisabled))
        prof.scores.forEach { s ->
            KrdsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.subject, style = KrdsText.cardTitle)
                        val parts = mutableListOf(s.examName)
                        if (s.percentile != "-") parts += "백분위 ${s.percentile}"
                        GradeLogic.parseInstant(s.timestamp)?.let { parts += kst(it) }
                        Text(parts.joinToString(" · "), style = KrdsText.meta)
                    }
                    Text("${s.score}점", style = KrdsText.sectionTitle, modifier = Modifier.padding(horizontal = 8.dp))
                    s.grade?.let { GradeBadge(it) }
                }
            }
        }
    }

    Section(if (testUser) "1" else "3", "과목별 등급컷")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KrdsChip("전체(학번+익명)", statsMode == GradeLogic.STATS_ALL) { statsMode = GradeLogic.STATS_ALL }
        KrdsChip("학번 인증만", statsMode == GradeLogic.STATS_VERIFIED) {
            if (!verified) app.toast("익명 사용자는 학번 인증 점수만의 통계를 볼 수 없습니다. 학번으로 로그인해 점수를 이전하면 볼 수 있어요.")
            else statsMode = GradeLogic.STATS_VERIFIED
        }
    }
    val statsSubjects = GradeLogic.statsSubjects(prof, testUser, isAdmin)
    if (statsSubjects.isEmpty()) {
        Text("점수를 제출한 과목만 조회할 수 있습니다. 먼저 점수를 제출하세요.", style = KrdsText.meta)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { statsSubjects.forEach { s -> KrdsChip(s, s == cutoffSubject) { cutoffSubject = s } } }
        cutoffSubject?.let { subject ->
            CutoffPanel(app, subject, statsMode, statsVersion, prof.scores.firstOrNull { it.subject == subject }?.score?.toDoubleOrNull())
        }
    }

    Section(if (testUser) "2" else "4", "과목/문항별 오답률")
    if (statsSubjects.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { statsSubjects.forEach { s -> KrdsChip(s, s == wrongSubject) { wrongSubject = s } } }
        ChoiceChips(
            listOf(
                WrongRateSort.QUESTION_ASC to "문항 번호순",
                WrongRateSort.QUESTION_DESC to "문항 역순",
                WrongRateSort.RATE_DESC to "오답률 높은순",
                WrongRateSort.RATE_ASC to "오답률 낮은순",
            ),
            wrongSort, { wrongSort = it },
        )
        wrongSubject?.let { subject -> WrongPanel(app, subject, statsMode, statsVersion, wrongSort, p) }
    }

    issuedCode?.let {
        AlertDialog(
            onDismissRequest = { issuedCode = null },
            title = { Text("접근 코드를 저장했습니다") },
            text = { Text("익명 접근 코드가 발급되어 이 기기에 자동으로 저장되었습니다. 다음에 들어오면 같은 익명 계정으로 이어집니다. 코드는 '접근 코드 보기'에서 지문(Face ID) 인증 후 확인할 수 있습니다.") },
            confirmButton = { TextButton(onClick = { issuedCode = null; revealCode() }) { Text("접근 코드 보기") } },
            dismissButton = { TextButton(onClick = { issuedCode = null }) { Text("확인") } },
        )
    }
    revealedCode?.let { code ->
        AlertDialog(
            onDismissRequest = { revealedCode = null },
            title = { Text("접근 코드: $code") },
            text = { Text("이 기기에 저장된 코드입니다. 다른 기기나 웹에서 '코드 로그인'에 입력하면 같은 익명 계정으로 들어갈 수 있습니다.") },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)); app.toast("코드를 복사했습니다."); revealedCode = null }) { Text("코드 복사") }
            },
            dismissButton = { TextButton(onClick = { revealedCode = null }) { Text("확인") } },
        )
    }
    confirm?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text("확인") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun Section(step: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
        Text(
            step,
            color = Krds.colors.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.background(Krds.colors.primary, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = KrdsText.sectionTitle)
    }
}

@Composable
private fun GradeBadge(grade: Int) {
    Badge(
        "${grade}등급",
        when {
            grade <= 2 -> BadgeTone.INFO
            grade <= 4 -> BadgeTone.SUCCESS
            grade <= 6 -> BadgeTone.NEUTRAL
            else -> BadgeTone.DANGER
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InputCard(s: RowState, policy: GradePolicy, profile: GradeProfile, onAction: (String) -> Unit) {
    val subject = s.subject
    val hasCurrent = subject != null && profile.current.containsKey(subject)
    val open = GradeLogic.openInfo(s.row.scheduleSubject.ifBlank { subject.orEmpty() }, policy)
    val count = subject?.let { GradeLogic.questionCount(it, policy) } ?: 0
    var menu by remember { mutableStateOf(false) }

    KrdsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (val row = s.row) {
                is InputRow.Fixed -> Text(row.subject, style = KrdsText.cardTitle, modifier = Modifier.weight(1f))
                is InputRow.Group -> Box(Modifier.weight(1f)) {
                    Row(Modifier.clickable { menu = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            subject ?: "${row.group.label.ifBlank { row.group.slotSubject.ifBlank { "선택 과목" } }} 선택",
                            style = KrdsText.cardTitle.copy(color = Krds.colors.primaryText),
                        )
                        Icon(Icons.Filled.ArrowDropDown, null, tint = Krds.colors.primaryText)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        row.group.subjects.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                menu = false
                                s.subject = option
                                val cur = profile.current[option]
                                s.score = cur?.score?.let(GradeLogic::formatScore).orEmpty()
                                s.wrong = cur?.wrongQuestions?.toSet().orEmpty()
                            })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = s.score,
                onValueChange = { v -> s.score = v.filter { it.isDigit() || it == '.' }.take(5) },
                enabled = subject != null && open.open,
                singleLine = true,
                placeholder = { Text("0~100") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.widthIn(max = 110.dp),
            )
        }
        if (subject != null && !open.open && open.availableAt != null) {
            Text("제출 가능: ${kst(open.availableAt!!)}", style = KrdsText.meta.copy(color = Krds.colors.danger), modifier = Modifier.padding(top = 4.dp))
        }
        if (subject != null && count > 0) {
            TextButton(onClick = { s.showWrong = !s.showWrong }) {
                Text(if (s.wrong.isEmpty()) "틀린 문항 선택 (선택 사항)" else "틀린 문항 ${s.wrong.size}개 선택됨", color = Krds.colors.primaryText)
            }
            if (s.showWrong) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (n in 1..count) {
                        KrdsChip(GradeLogic.questionLabel(subject, n, policy), n in s.wrong) {
                            s.wrong = if (n in s.wrong) s.wrong - n else s.wrong + n
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PrimaryButton("제출", { onAction("submit") }, Modifier.weight(1f), enabled = subject != null && !hasCurrent && open.open)
            SecondaryButton("수정", { onAction("update") }, Modifier.weight(1f), enabled = hasCurrent)
            SecondaryButton("삭제", { onAction("delete") }, Modifier.weight(1f), enabled = hasCurrent, danger = true)
        }
    }
}

@Composable
private fun CutoffPanel(app: AppState, subject: String, statsMode: String, version: Int, mine: Double?) {
    var result by remember { mutableStateOf<Result<CutoffResult>?>(null) }
    LaunchedEffect(subject, statsMode, version) {
        result = null
        result = runCatching { app.services.grades.cutoff(subject, statsMode) }
    }
    val r = result ?: return Loading()
    r.onFailure { Text(it.userMessage(), style = KrdsText.meta) }
    val c = r.getOrNull() ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetaTile("표본 수", "${c.sampleCount}명", Modifier.weight(1f))
        MetaTile("평균", "${c.averageScore.fixed(2)}점", Modifier.weight(1f))
        if (mine != null) MetaTile("내 점수", "${GradeLogic.formatScore(mine)}점", Modifier.weight(1f))
    }
    if (!c.visible) {
        val parts = mutableListOf("등급컷이 아직 공개되지 않았습니다.")
        if (c.requiredSampleCount > c.sampleCount) parts += "표본이 ${c.requiredSampleCount - c.sampleCount}명 더 필요합니다."
        GradeLogic.parseInstant(c.publicationTime)?.let { parts += "예정 공개 시간: ${kst(it)}" }
        InfoBox(parts.joinToString("\n"))
        return
    }
    Text(if (c.rows.isEmpty()) "데이터가 없습니다." else "등급컷 공개 중", style = KrdsText.meta)
    val myGrade = GradeLogic.myGrade(c.rows, mine)
    KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        c.rows.forEach { row ->
            val isMine = myGrade == row.grade
            Column(Modifier.fillMaxWidth().background(if (isMine) Krds.colors.primaryTint else Krds.colors.surface).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradeBadge(row.grade)
                    Spacer(Modifier.width(10.dp))
                    LinearProgressIndicator(
                        progress = { ((row.score ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = Krds.colors.primary,
                        trackColor = Krds.colors.surfaceRaised,
                    )
                    Text(row.score?.let { "${it.fixed(1)}점" } ?: "X (산출불가)", style = KrdsText.body, modifier = Modifier.padding(start = 10.dp).widthIn(min = 64.dp))
                }
                if (isMine && mine != null) Text("내 점수 ${GradeLogic.formatScore(mine)}점이 이 등급입니다.", style = KrdsText.meta.copy(color = Krds.colors.primaryText))
            }
        }
    }
}

@Composable
private fun WrongPanel(app: AppState, subject: String, statsMode: String, version: Int, sort: WrongRateSort, policy: GradePolicy) {
    var result by remember { mutableStateOf<Result<Pair<List<WrongRate>, Int>>?>(null) }
    LaunchedEffect(subject, statsMode, version) {
        result = null
        result = runCatching { app.services.grades.wrongRates(subject, statsMode) }
    }
    val r = result ?: return Loading()
    r.onFailure { Text(it.userMessage(), style = KrdsText.meta) }
    val (rates, responses) = r.getOrNull() ?: return
    MetaTile("응답 수", "${responses}명")
    val sorted = GradeLogic.sortWrongRates(rates, sort)
    if (sorted.isEmpty()) return Text("데이터가 없습니다.", style = KrdsText.meta)
    KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        sorted.forEach { w ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge("${GradeLogic.questionLabel(subject, w.questionNo, policy)}번", BadgeTone.NEUTRAL)
                    Spacer(Modifier.width(10.dp))
                    LinearProgressIndicator(
                        progress = { (w.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = when {
                            w.percentage >= 60 -> Krds.colors.danger
                            w.percentage >= 40 -> Krds.colors.star
                            else -> Krds.colors.primary
                        },
                        trackColor = Krds.colors.surfaceRaised,
                    )
                    Text("${w.percentage.fixed(2)}%", style = KrdsText.body, modifier = Modifier.padding(start = 10.dp).widthIn(min = 64.dp))
                }
                Text("${w.wrong}명 / ${w.responses}명 오답", style = KrdsText.meta)
            }
        }
    }
}
