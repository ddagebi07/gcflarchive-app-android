package kr.co.gcflarchive.student.survey

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kr.co.gcflarchive.core.community.CommunityParser
import kr.co.gcflarchive.core.survey.Answer
import kr.co.gcflarchive.core.survey.QuestionType
import kr.co.gcflarchive.core.survey.ScopeOption
import kr.co.gcflarchive.core.survey.SurveyDraft
import kr.co.gcflarchive.core.survey.SurveyException
import kr.co.gcflarchive.core.survey.SurveyForm
import kr.co.gcflarchive.core.survey.SurveyList
import kr.co.gcflarchive.core.survey.SurveyLogic
import kr.co.gcflarchive.core.survey.SurveyQuestion
import kr.co.gcflarchive.core.survey.SurveyResults
import kr.co.gcflarchive.core.survey.SurveySummary
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.platform.saveFile
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.InfoBox
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsDropdown
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.Loading
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.SecondaryButton
import kr.co.gcflarchive.student.ui.StateMessage
import kotlin.time.Duration.Companion.hours

private fun messageOf(e: Throwable) = e.message ?: "인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요."
private fun time(iso: String) = CommunityParser.displayTime(iso, Clock.System.now())

// ── 목록 ──────────────────────────────────────────────────────────────────

/** 설문조사: 참여할 수 있는 설문과 내가 만든 설문. 만들 수 있는 사람에게는 '설문 만들기'. */
@Composable
fun SurveyListScreen() {
    val app = LocalApp.current
    var data by remember { mutableStateOf<Result<SurveyList>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload, app.sessionVersion, app.contentVersion) { data = runCatching { app.services.surveys.list() } }

    ScreenScaffold("설문조사") {
        when (val r = data) {
            null -> Loading()
            else -> r.fold(
                onFailure = { e -> StateMessage("설문을 불러오지 못했어요.", messageOf(e), "다시 불러오기", { reload++ }) },
                onSuccess = { list ->
                    if (!list.loggedIn) {
                        StateMessage("로그인하면 설문에 참여할 수 있어요.", null, "로그인하기", { app.requireLogin() }, locked = true)
                        return@fold
                    }
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("참여할 수 있는 설문", style = KrdsText.sectionTitle)
                            if (list.surveys.isEmpty()) Text("지금 참여할 수 있는 설문이 없어요.", style = KrdsText.meta)
                            list.surveys.forEach { s -> SurveyCard(s) { app.push(Route.Survey(s.id)) } }
                            if (list.canCreate || list.mine.isNotEmpty()) {
                                Text("내가 만든 설문", style = KrdsText.sectionTitle, modifier = Modifier.padding(top = 12.dp))
                                if (list.mine.isEmpty()) Text("아래 '설문 만들기'로 첫 설문을 만들어보세요.", style = KrdsText.meta)
                                list.mine.forEach { s -> SurveyCard(s) { app.push(Route.Survey(s.id)) } }
                            }
                            Box(Modifier.heightIn(min = 72.dp))
                        }
                        if (list.canCreate) {
                            ExtendedFloatingActionButton(
                                onClick = { app.push(Route.SurveyBuild(0)) },
                                containerColor = Krds.colors.primary,
                                contentColor = Krds.colors.onPrimary,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                            ) {
                                Icon(Icons.Filled.Add, null)
                                Text("설문 만들기", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SurveyCard(s: SurveySummary, onClick: () -> Unit) {
    KrdsCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.title, style = KrdsText.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Badge(s.statusLabel, tone = when (s.status) { "open" -> BadgeTone.SUCCESS; "draft" -> BadgeTone.NEUTRAL; else -> BadgeTone.NEUTRAL })
        }
        if (s.description.isNotBlank()) Text(s.description, style = KrdsText.body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(
            listOfNotNull(
                s.ownerName.ifBlank { null },
                "문항 ${s.questionCount}개",
                s.responseCount?.let { "응답 ${it}개" },
                if (s.closesAt.isNotBlank() && s.status == "open") time(s.closesAt) + " 마감" else null,
                if (s.responded) "응답함" else null,
            ).joinToString(" · "),
            style = KrdsText.meta,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ── 응답하기 ───────────────────────────────────────────────────────────────

/** 설문 응답: 문항 종류별 입력. 만든 사람에게는 결과·수정·마감·삭제 메뉴. */
@Composable
fun SurveyScreen(id: Int) {
    val app = LocalApp.current
    val repo = app.services.surveys
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<Result<SurveyForm>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<String, Answer>() }
    var editing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id, reload, app.sessionVersion) {
        val r = runCatching { repo.form(id) }
        r.getOrNull()?.myAnswers?.let { mine -> answers.clear(); answers.putAll(mine) }
        data = r
    }
    val form = data?.getOrNull()

    fun submit(f: SurveyForm) {
        problem = SurveyLogic.firstProblem(f.questions, answers)
        if (problem != null) return
        sending = true
        scope.launch {
            runCatching { repo.submit(id, answers.toMap()) }
                .onSuccess { updated ->
                    submitted = true
                    editing = false
                    app.toast(if (updated) "응답을 수정했어요." else "응답을 제출했어요.")
                    app.contentChanged()
                    reload++
                }
                .onFailure { e -> if (e is SurveyException && e.loginRequired) app.requireLogin() else problem = "" to messageOf(e) }
            sending = false
        }
    }

    fun setStatus(status: String) {
        scope.launch {
            runCatching { repo.setStatus(id, status) }
                .onSuccess { app.toast(if (status == "closed") "설문을 마감했어요." else "설문을 다시 열었어요."); app.contentChanged(); reload++ }
                .onFailure { app.toast(messageOf(it)) }
        }
    }

    ScreenScaffold(
        "설문조사",
        actions = {
            if (form?.summary?.canManage == true) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "설문 메뉴") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("결과 보기") }, onClick = { menu = false; app.push(Route.SurveyResults(id)) })
                    DropdownMenuItem(text = { Text("수정") }, onClick = { menu = false; app.push(Route.SurveyBuild(id)) })
                    when (form.rawStatus) {
                        "open" -> DropdownMenuItem(text = { Text("마감하기") }, onClick = { menu = false; setStatus("closed") })
                        else -> DropdownMenuItem(text = { Text(if (form.rawStatus == "draft") "게시하기" else "다시 열기") }, onClick = { menu = false; setStatus("open") })
                    }
                    DropdownMenuItem(text = { Text("삭제", color = Krds.colors.danger) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        },
    ) {
        when (val r = data) {
            null -> Loading()
            else -> r.fold(
                onFailure = { e ->
                    if (e is SurveyException && e.loginRequired) StateMessage("로그인하면 설문에 참여할 수 있어요.", null, "로그인하기", { app.requireLogin() }, locked = true)
                    else StateMessage("설문을 열지 못했어요.", messageOf(e), "다시 불러오기", { reload++ })
                },
                onSuccess = { f ->
                    val answered = f.myAnswers != null
                    val inputEnabled = f.canRespond && (!answered || editing)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 머리: 제목·설명 (구글 폼의 상단 카드처럼 위에 굵은 선)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Krds.colors.surface)
                                .border(1.dp, Krds.colors.border, RoundedCornerShape(8.dp)),
                        ) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 6.dp).background(Krds.colors.primary))
                            Column(Modifier.padding(16.dp)) {
                                Text(f.summary.title, style = KrdsText.pageTitle)
                                if (f.description.isNotBlank()) Text(f.description, style = KrdsText.body, modifier = Modifier.padding(top = 8.dp))
                                Text(
                                    listOfNotNull(
                                        f.summary.ownerName.ifBlank { null },
                                        f.summary.statusLabel,
                                        if (f.summary.closesAt.isNotBlank()) time(f.summary.closesAt) + " 마감" else null,
                                    ).joinToString(" · "),
                                    style = KrdsText.meta,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                if (f.questions.any { it.required }) Text("* 표시는 꼭 답해야 하는 문항이에요.", style = KrdsText.meta.copy(color = Krds.colors.danger), modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        if (f.summary.collectIdentity) InfoBox("이 설문은 응답한 사람의 학번을 설문을 만든 사람에게 보여줘요.", icon = Icons.Filled.Info)
                        if (!f.accepting && !f.summary.canManage) InfoBox(if (f.rawStatus == "draft") "아직 게시되지 않은 설문이에요." else "마감된 설문이에요.")
                        if (answered && !editing) {
                            InfoBox(if (submitted) "응답을 제출했어요. 참여해 주셔서 고마워요." else "이미 응답한 설문이에요. 아래는 내 응답이에요.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (f.canRespond) SecondaryButton("응답 수정", { editing = true }, Modifier.weight(1f), icon = Icons.Filled.Edit)
                                if (f.canSeeSummary) SecondaryButton("결과 요약 보기", { app.push(Route.SurveyResults(id)) }, Modifier.weight(1f))
                            }
                        }
                        f.questions.forEach { q ->
                            QuestionCard(q, answers[q.id], inputEnabled, error = problem?.takeIf { it.first == q.id }?.second) { a ->
                                if (a == null) answers.remove(q.id) else answers[q.id] = a
                                if (problem?.first == q.id) problem = null
                            }
                        }
                        problem?.takeIf { it.first.isEmpty() }?.let { Text(it.second, style = KrdsText.body.copy(color = Krds.colors.danger)) }
                        if (inputEnabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (editing) SecondaryButton("취소", { editing = false; answers.clear(); f.myAnswers?.let(answers::putAll) }, Modifier.weight(1f))
                                PrimaryButton(if (editing) "수정한 응답 제출" else "제출", { submit(f) }, Modifier.weight(1f), enabled = !sending)
                            }
                            problem?.takeIf { it.first.isNotEmpty() }?.let { Text(it.second, style = KrdsText.body.copy(color = Krds.colors.danger)) }
                        }
                        Box(Modifier.heightIn(min = 24.dp))
                    }
                },
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text("설문과 모든 응답을 삭제할까요? 삭제한 뒤에는 되살릴 수 없어요.") },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("닫기") } },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { repo.delete(id) }.onSuccess { app.toast("설문을 삭제했어요."); app.contentChanged(); app.pop() }.onFailure { app.toast(messageOf(it)) }
                    }
                }) { Text("삭제", color = Krds.colors.danger) }
            },
        )
    }
}

/** One question, Google Forms style: a card with the title (* when required) and its input. */
@Composable
private fun QuestionCard(q: SurveyQuestion, answer: Answer?, enabled: Boolean, error: String?, onAnswer: (Answer?) -> Unit) {
    if (q.type == QuestionType.SECTION) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 2.dp).background(Krds.colors.textStrong))
            Text(q.title, style = KrdsText.sectionTitle, modifier = Modifier.padding(top = 8.dp))
            if (q.description.isNotBlank()) Text(q.description, style = KrdsText.body, modifier = Modifier.padding(top = 4.dp))
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Krds.colors.surface)
            .border(if (error != null) 2.dp else 1.dp, if (error != null) Krds.colors.danger else Krds.colors.border, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            q.title + if (q.required) " *" else "",
            style = KrdsText.cardTitle.copy(color = Krds.colors.textStrong),
        )
        if (q.description.isNotBlank()) Text(q.description, style = KrdsText.meta)
        when (q.type) {
            QuestionType.SHORT, QuestionType.PARAGRAPH -> OutlinedTextField(
                value = (answer as? Answer.Text)?.value.orEmpty(),
                onValueChange = { v -> if (v.length <= if (q.type == QuestionType.SHORT) 500 else 5000) onAnswer(Answer.Text(v)) },
                placeholder = { Text(if (q.type == QuestionType.SHORT) "단답형 텍스트" else "장문형 텍스트") },
                singleLine = q.type == QuestionType.SHORT,
                minLines = if (q.type == QuestionType.PARAGRAPH) 4 else 1,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            QuestionType.CHOICE -> ChoiceList(q, (answer as? Answer.Text)?.value?.let(::listOf).orEmpty(), multiple = false, enabled) { picks ->
                onAnswer(picks.firstOrNull()?.let { Answer.Text(it) })
            }
            QuestionType.CHECKBOX -> ChoiceList(q, (answer as? Answer.Picks)?.values.orEmpty(), multiple = true, enabled) { picks ->
                onAnswer(if (picks.isEmpty()) null else Answer.Picks(picks))
            }
            QuestionType.DROPDOWN -> KrdsDropdown(
                "선택", listOf("" to "선택하지 않음") + q.options.map { it to it }, (answer as? Answer.Text)?.value.orEmpty(),
                { v -> if (enabled) onAnswer(v.ifBlank { null }?.let { Answer.Text(it) }) }, Modifier.fillMaxWidth(),
            )
            QuestionType.SCALE -> ScaleInput(q, (answer as? Answer.Number)?.value, enabled) { onAnswer(it?.let { n -> Answer.Number(n) }) }
            QuestionType.DATE -> OutlinedTextField(
                value = (answer as? Answer.Text)?.value.orEmpty(),
                onValueChange = { v -> if (v.length <= 10) onAnswer(v.ifBlank { null }?.let { Answer.Text(it) }) },
                label = { Text("날짜 (예: 2026-10-20)") },
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            )
            QuestionType.TIME -> OutlinedTextField(
                value = (answer as? Answer.Text)?.value.orEmpty(),
                onValueChange = { v -> if (v.length <= 5) onAnswer(v.ifBlank { null }?.let { Answer.Text(it) }) },
                label = { Text("시간 (예: 13:30)") },
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            )
            QuestionType.SECTION -> Unit
        }
        if (error != null) Text(error, style = KrdsText.meta.copy(color = Krds.colors.danger))
    }
}

/** Radio / checkbox rows, with '기타' and its text field when the question allows it. */
@Composable
private fun ChoiceList(q: SurveyQuestion, picked: List<String>, multiple: Boolean, enabled: Boolean, onChange: (List<String>) -> Unit) {
    val otherValue = picked.firstOrNull { it !in q.options }
    var otherText by remember(q.id) { mutableStateOf(otherValue.orEmpty()) }
    var otherOn by remember(q.id) { mutableStateOf(otherValue != null) }
    fun emit(base: List<String>, other: Boolean, text: String) {
        val inList = base.filter { it in q.options }
        onChange(if (other && text.isNotBlank()) inList + text.trim() else inList)
    }
    q.options.forEach { option ->
        val selected = option in picked
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .then(
                    if (multiple) Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox) {
                        emit(if (selected) picked - option else picked + option, otherOn, otherText)
                    } else Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton) {
                        otherOn = false
                        emit(listOf(option), false, otherText)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multiple) Checkbox(selected, null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = Krds.colors.primary))
            else RadioButton(selected, null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = Krds.colors.primary))
            Text(option, style = KrdsText.body.copy(color = Krds.colors.textStrong), modifier = Modifier.padding(start = 12.dp))
        }
    }
    if (q.other) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.heightIn(min = 48.dp).then(
                    if (multiple) Modifier.toggleable(otherOn, enabled = enabled, role = Role.Checkbox) { otherOn = it; emit(picked, it, otherText) }
                    else Modifier.selectable(otherOn, enabled = enabled, role = Role.RadioButton) { otherOn = true; emit(emptyList(), true, otherText) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (multiple) Checkbox(otherOn, null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = Krds.colors.primary))
                else RadioButton(otherOn, null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = Krds.colors.primary))
                Text("기타:", style = KrdsText.body.copy(color = Krds.colors.textStrong), modifier = Modifier.padding(start = 12.dp, end = 8.dp))
            }
            OutlinedTextField(
                value = otherText,
                onValueChange = { v ->
                    if (v.length <= 200) {
                        otherText = v
                        otherOn = true
                        emit(if (multiple) picked else emptyList(), true, v)
                    }
                },
                singleLine = true, enabled = enabled, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 선형 배율: numbers in a row with the end labels underneath. */
@Composable
private fun ScaleInput(q: SurveyQuestion, value: Int?, enabled: Boolean, onChange: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (q.min..q.max).forEach { n ->
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp).selectable(value == n, enabled = enabled, role = Role.RadioButton) { onChange(n) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$n", style = KrdsText.label.copy(color = Krds.colors.textStrong))
                RadioButton(value == n, null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = Krds.colors.primary))
            }
        }
    }
    if (q.minLabel.isNotBlank() || q.maxLabel.isNotBlank()) {
        Row(Modifier.fillMaxWidth()) {
            Text(q.minLabel, style = KrdsText.meta, modifier = Modifier.weight(1f))
            Text(q.maxLabel, style = KrdsText.meta)
        }
    }
}

// ── 결과 ───────────────────────────────────────────────────────────────────

/** 결과: 문항별 요약 (막대·평균·주관식 목록). 만든 사람은 응답 하나씩 보기와 CSV 받기도 돼요. */
@Composable
fun SurveyResultsScreen(id: Int) {
    val app = LocalApp.current
    val repo = app.services.surveys
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<Result<SurveyResults>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, reload) { data = runCatching { repo.results(id) } }

    ScreenScaffold("설문 결과") {
        when (val r = data) {
            null -> Loading()
            else -> r.fold(
                onFailure = { e -> StateMessage("결과를 불러오지 못했어요.", messageOf(e), "다시 불러오기", { reload++ }) },
                onSuccess = { res ->
                    val owner = res.questions.isNotEmpty()
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(res.title, style = KrdsText.pageTitle)
                        Text("응답 ${res.responseCount}개", style = KrdsText.sectionTitle)
                        if (owner) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (tab == 0) PrimaryButton("요약", {}, Modifier.weight(1f)) else SecondaryButton("요약", { tab = 0 }, Modifier.weight(1f))
                                if (tab == 1) PrimaryButton("개별 응답", {}, Modifier.weight(1f)) else SecondaryButton("개별 응답", { tab = 1 }, Modifier.weight(1f))
                            }
                            SecondaryButton("CSV로 받기", {
                                scope.launch {
                                    runCatching { saveFile("survey-$id.csv", repo.csv(id), "text/csv") }
                                        .onSuccess { app.toast("CSV 파일을 저장했어요.") }
                                        .onFailure { app.toast(messageOf(it)) }
                                }
                            }, Modifier.fillMaxWidth(), icon = Icons.Filled.Download)
                        }
                        if (res.responseCount == 0) {
                            Text("아직 응답이 없어요.", style = KrdsText.meta)
                        } else if (tab == 0 || !owner) {
                            res.summary.forEach { s -> SummaryCard(s) }
                        } else {
                            val row = res.responses.getOrNull(index.coerceIn(0, res.responses.size - 1))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SecondaryButton("이전", { index = (index - 1).coerceAtLeast(0) }, enabled = index > 0)
                                Text("${index + 1} / ${res.responses.size}", style = KrdsText.label, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                                SecondaryButton("다음", { index = (index + 1).coerceAtMost(res.responses.size - 1) }, enabled = index < res.responses.size - 1)
                            }
                            if (row != null) {
                                KrdsCard {
                                    Text(
                                        listOfNotNull(if (res.collectIdentity && row.respondent.isNotBlank()) "학번 ${row.respondent}" else null, time(row.submittedAt))
                                            .joinToString(" · "),
                                        style = KrdsText.meta,
                                    )
                                    res.questions.forEach { (qid, title) ->
                                        Text(title, style = KrdsText.label.copy(color = Krds.colors.textStrong), modifier = Modifier.padding(top = 12.dp))
                                        Text(row.answers[qid].orEmpty().ifBlank { "응답 없음" }, style = KrdsText.body)
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SummaryCard(s: kr.co.gcflarchive.core.survey.QuestionSummary) {
    KrdsCard {
        Text(s.title, style = KrdsText.cardTitle)
        Text("응답 ${s.answered}개", style = KrdsText.meta, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        when {
            s.options.isNotEmpty() -> {
                val total = s.options.sumOf { it.count }.coerceAtLeast(1)
                s.options.forEach { o -> CountBar(o.text, o.count, o.count * 100 / total) }
                if (s.otherTexts.isNotEmpty()) {
                    Text("기타 응답", style = KrdsText.label, modifier = Modifier.padding(top = 8.dp))
                    s.otherTexts.take(20).forEach { Text("· $it", style = KrdsText.body) }
                }
            }
            s.scale.isNotEmpty() -> {
                s.average?.let { Text("평균 $it", style = KrdsText.label.copy(color = Krds.colors.textStrong)) }
                val total = s.scale.sumOf { it.second }.coerceAtLeast(1)
                s.scale.forEach { (v, c) ->
                    val label = when (v) {
                        s.scale.first().first -> listOf("$v", s.minLabel).filter { it.isNotBlank() }.joinToString(" ")
                        s.scale.last().first -> listOf("$v", s.maxLabel).filter { it.isNotBlank() }.joinToString(" ")
                        else -> "$v"
                    }
                    CountBar(label, c, c * 100 / total)
                }
            }
            else -> {
                if (s.texts.isEmpty()) Text("아직 응답이 없어요.", style = KrdsText.meta)
                s.texts.take(50).forEach { t ->
                    Text(t, style = KrdsText.body, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Hairline()
                }
            }
        }
    }
}

/** A light bar behind dark text, so labels stay readable at any width. */
@Composable
private fun CountBar(label: String, count: Int, percent: Int) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).heightIn(min = 40.dp).clip(RoundedCornerShape(6.dp))
            .border(1.dp, Krds.colors.border, RoundedCornerShape(6.dp)),
    ) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().background(Krds.colors.primaryTint))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = KrdsText.body.copy(color = Krds.colors.textStrong), modifier = Modifier.weight(1f))
            Text("${count}명 · $percent%", style = KrdsText.label.copy(color = Krds.colors.textStrong))
        }
    }
}

// ── 만들기·수정 ────────────────────────────────────────────────────────────

private val DEADLINES = listOf(0 to "마감 없음", 24 to "하루 뒤", 72 to "3일 뒤", 168 to "일주일 뒤", 336 to "2주 뒤", 720 to "30일 뒤")

private class QuestionDraft(q: SurveyQuestion) {
    val id = q.id
    var type by mutableStateOf(q.type)
    var title by mutableStateOf(q.title)
    var description by mutableStateOf(q.description)
    var required by mutableStateOf(q.required)
    var options by mutableStateOf(q.options.ifEmpty { listOf("") })
    var other by mutableStateOf(q.other)
    var min by mutableStateOf(q.min)
    var max by mutableStateOf(q.max)
    var minLabel by mutableStateOf(q.minLabel)
    var maxLabel by mutableStateOf(q.maxLabel)

    fun build() = SurveyQuestion(id, type, title, description, required && type != QuestionType.SECTION,
        if (type.hasOptions) options else emptyList(), other && (type == QuestionType.CHOICE || type == QuestionType.CHECKBOX),
        min, max, minLabel, maxLabel)
}

/** 설문 만들기·수정 (구글 폼처럼): 제목·설명, 설정, 문항 카드 (종류·질문·선택지·필수), 순서 바꾸기. */
@Composable
fun SurveyBuilderScreen(id: Int) {
    val app = LocalApp.current
    val repo = app.services.surveys
    val scope = rememberCoroutineScope()
    val editing = id > 0
    var loaded by remember { mutableStateOf(!editing) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var scopes by remember { mutableStateOf<List<ScopeOption>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf(setOf("login")) }
    var deadline by remember { mutableIntStateOf(0) }
    var keepCloses by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("draft") }
    var collectIdentity by remember { mutableStateOf(false) }
    var allowEdit by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    val questions = remember { mutableStateListOf<QuestionDraft>() }
    var nextId by remember { mutableIntStateOf(1) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun newQuestion(type: QuestionType) = QuestionDraft(SurveyQuestion("n${nextId++}", type, "", options = if (type.hasOptions) listOf("옵션 1") else emptyList()))

    LaunchedEffect(id) {
        runCatching { repo.list() }.getOrNull()?.let { scopes = it.scopes }
        if (editing) {
            runCatching { repo.form(id) }
                .onSuccess { f ->
                    title = f.summary.title; description = f.description; status = f.rawStatus
                    audience = f.audienceScope.toSet().ifEmpty { setOf("login") }
                    keepCloses = f.summary.closesAt
                    collectIdentity = f.summary.collectIdentity; allowEdit = f.allowEdit; showSummary = f.showSummary
                    questions.clear(); questions.addAll(f.questions.map(::QuestionDraft))
                    loaded = true
                }
                .onFailure { loadError = messageOf(it) }
        } else if (questions.isEmpty()) {
            questions.add(newQuestion(QuestionType.CHOICE))
        }
    }

    fun save(publish: Boolean?) {
        val closes = if (deadline > 0) (Clock.System.now() + deadline.hours).toString() else keepCloses
        val draft = SurveyDraft(
            title, description, audience.toList(), if (publish == null) status else if (publish) "open" else "draft",
            closes, collectIdentity, allowEdit, showSummary, questions.map { it.build() },
        )
        error = SurveyLogic.draftProblem(draft)
        if (error != null) return
        saving = true
        scope.launch {
            runCatching { if (editing) { repo.update(id, draft); id } else repo.create(draft) }
                .onSuccess { sid ->
                    app.toast(if (draft.status == "open") "설문을 게시했어요." else "설문을 저장했어요.")
                    app.contentChanged()
                    app.pop()
                    if (!editing && sid > 0) app.push(Route.Survey(sid))
                }
                .onFailure { error = messageOf(it) }
            saving = false
        }
    }

    ScreenScaffold(if (editing) "설문 수정" else "설문 만들기") {
        when {
            loadError != null -> StateMessage("설문을 열지 못했어요.", loadError, null, null)
            !loaded -> Loading()
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Krds.colors.surface)
                        .border(1.dp, Krds.colors.border, RoundedCornerShape(8.dp)),
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 6.dp).background(Krds.colors.primary))
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(title, { if (it.length <= 200) title = it }, label = { Text("설문 제목") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(description, { if (it.length <= 2000) description = it }, label = { Text("설문 설명") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    }
                }

                KrdsCard {
                    Text("설정", style = KrdsText.cardTitle)
                    Text("응답할 수 있는 사람", style = KrdsText.label.copy(color = Krds.colors.textStrong), modifier = Modifier.padding(top = 8.dp))
                    Text("여러 개를 고르면 그중 하나에만 맞아도 돼요.", style = KrdsText.meta)
                    (scopes.ifEmpty { listOf(ScopeOption("login", "로그인한 사람")) }).forEach { o ->
                        CheckRow(o.label, o.value in audience) { on -> audience = if (on) audience + o.value else audience - o.value }
                    }
                    KrdsDropdown(
                        "마감", DEADLINES.map { it.first to it.second } + listOfNotNull(if (keepCloses.isNotBlank()) -1 to "지금 마감 시각 유지 (${time(keepCloses)})" else null),
                        if (deadline == 0 && keepCloses.isNotBlank()) -1 else deadline,
                        { v -> if (v == -1) deadline = 0 else { deadline = v; keepCloses = "" } },
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    SwitchRow("응답자 학번 수집", "켜면 응답 화면에 미리 알리고, 결과에서 학번이 보여요.", collectIdentity) { collectIdentity = it }
                    SwitchRow("응답 수정 허용", "제출한 뒤에도 마감 전까지 응답을 수정할 수 있어요.", allowEdit) { allowEdit = it }
                    SwitchRow("응답자에게 결과 요약 보여주기", "제출한 사람도 문항별 요약을 볼 수 있어요.", showSummary) { showSummary = it }
                }

                questions.forEachIndexed { i, q ->
                    QuestionEditor(
                        q, i, questions.size,
                        onMove = { delta ->
                            val j = i + delta
                            if (j in questions.indices) { val item = questions.removeAt(i); questions.add(j, item) }
                        },
                        onDelete = { questions.removeAt(i) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("문항 추가", { questions.add(newQuestion(QuestionType.CHOICE)) }, Modifier.weight(1f), icon = Icons.Filled.Add)
                    SecondaryButton("구역 추가", { questions.add(newQuestion(QuestionType.SECTION)) }, Modifier.weight(1f))
                }
                error?.let { Text(it, style = KrdsText.body.copy(color = Krds.colors.danger)) }
                if (editing) {
                    PrimaryButton("저장", { save(null) }, Modifier.fillMaxWidth(), enabled = !saving)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("임시 저장", { save(false) }, Modifier.weight(1f), enabled = !saving)
                        PrimaryButton("게시하기", { save(true) }, Modifier.weight(1f), enabled = !saving)
                    }
                }
                Box(Modifier.heightIn(min = 24.dp))
            }
        }
    }
}

@Composable
private fun QuestionEditor(q: QuestionDraft, index: Int, count: Int, onMove: (Int) -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Krds.colors.surface)
            .border(1.dp, Krds.colors.border, RoundedCornerShape(8.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (q.type == QuestionType.SECTION) "구역" else "${index + 1}번 문항", style = KrdsText.label.copy(color = Krds.colors.textSecondary), modifier = Modifier.weight(1f))
            IconButton(onClick = { onMove(-1) }, enabled = index > 0) { Icon(Icons.Filled.ArrowUpward, "위로", tint = Krds.colors.textSecondary) }
            IconButton(onClick = { onMove(1) }, enabled = index < count - 1) { Icon(Icons.Filled.ArrowDownward, "아래로", tint = Krds.colors.textSecondary) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "문항 삭제", tint = Krds.colors.danger) }
        }
        if (q.type != QuestionType.SECTION) {
            KrdsDropdown("문항 종류", QuestionType.entries.filter { it != QuestionType.SECTION }.map { it to it.label }, q.type,
                { t -> q.type = t; if (t.hasOptions && q.options.all { it.isBlank() }) q.options = listOf("옵션 1") }, Modifier.fillMaxWidth())
        }
        OutlinedTextField(q.title, { if (it.length <= 300) q.title = it }, label = { Text(if (q.type == QuestionType.SECTION) "구역 제목" else "질문") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(q.description, { if (it.length <= 2000) q.description = it }, label = { Text("설명 (선택)") }, modifier = Modifier.fillMaxWidth())
        if (q.type.hasOptions) {
            q.options.forEachIndexed { i, o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(o, { v -> if (v.length <= 200) q.options = q.options.toMutableList().also { it[i] = v } },
                        label = { Text("옵션 ${i + 1}") }, singleLine = true, modifier = Modifier.weight(1f))
                    if (q.options.size > 1) IconButton(onClick = { q.options = q.options.toMutableList().also { it.removeAt(i) } }) {
                        Icon(Icons.Filled.Delete, "옵션 ${i + 1} 삭제", tint = Krds.colors.textSecondary)
                    }
                }
            }
            if (q.options.size < 50) SecondaryButton("옵션 추가", { q.options = q.options + "옵션 ${q.options.size + 1}" }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            if (q.type != QuestionType.DROPDOWN) CheckRow("'기타' 직접 입력 받기", q.other) { q.other = it }
        }
        if (q.type == QuestionType.SCALE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KrdsDropdown("시작", listOf(0 to "0", 1 to "1"), q.min, { q.min = it }, Modifier.weight(1f))
                KrdsDropdown("끝", (2..10).map { it to "$it" }, q.max, { q.max = it }, Modifier.weight(1f))
            }
            OutlinedTextField(q.minLabel, { if (it.length <= 60) q.minLabel = it }, label = { Text("${q.min} 라벨 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(q.maxLabel, { if (it.length <= 60) q.maxLabel = it }, label = { Text("${q.max} 라벨 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (q.type != QuestionType.SECTION) SwitchRow("필수", null, q.required) { q.required = it }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, null, colors = CheckboxDefaults.colors(checkedColor = Krds.colors.primary))
        Text(label, style = KrdsText.body.copy(color = Krds.colors.textStrong), modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KrdsText.body.copy(color = Krds.colors.textStrong, fontWeight = FontWeight.Medium))
            if (desc != null) Text(desc, style = KrdsText.meta)
        }
        Switch(checked, null, colors = SwitchDefaults.colors(checkedTrackColor = Krds.colors.primary))
    }
}
