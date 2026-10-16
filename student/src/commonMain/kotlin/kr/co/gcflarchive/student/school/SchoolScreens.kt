package kr.co.gcflarchive.student.school

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kr.co.gcflarchive.core.community.CommunityParser
import kr.co.gcflarchive.core.net.LoginRequiredException
import kr.co.gcflarchive.core.net.UploadFile
import kr.co.gcflarchive.core.school.Audience
import kr.co.gcflarchive.core.school.ClassRef
import kr.co.gcflarchive.core.school.FreeTeachers
import kr.co.gcflarchive.core.school.GroupList
import kr.co.gcflarchive.core.school.MessageDetail
import kr.co.gcflarchive.core.school.SchoolFile
import kr.co.gcflarchive.core.school.SchoolGroup
import kr.co.gcflarchive.core.school.SchoolMe
import kr.co.gcflarchive.core.school.SchoolMessage
import kr.co.gcflarchive.core.school.SchoolNotification
import kr.co.gcflarchive.core.school.SchoolRole
import kr.co.gcflarchive.core.school.SubRequest
import kr.co.gcflarchive.core.school.SubRequests
import kr.co.gcflarchive.core.school.TargetKind
import kr.co.gcflarchive.core.school.TeacherCard
import kr.co.gcflarchive.core.school.ThreadDetail
import kr.co.gcflarchive.core.school.ThreadSummary
import kr.co.gcflarchive.core.school.TtCell
import kr.co.gcflarchive.core.school.TtWeek
import kr.co.gcflarchive.core.school.SUB_STATUS
import kr.co.gcflarchive.core.school.VISIT_STATUS
import kr.co.gcflarchive.core.school.Visit
import kr.co.gcflarchive.core.school.VisitOptions
import kr.co.gcflarchive.core.school.parseTarget
import kr.co.gcflarchive.core.school.teacherLabel
import kr.co.gcflarchive.core.school.visitDayText
import kr.co.gcflarchive.student.AppState
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.platform.rememberFilePicker
import kr.co.gcflarchive.student.platform.saveFile
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.InfoBox
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsChip
import kr.co.gcflarchive.student.ui.KrdsDropdown
import kr.co.gcflarchive.student.ui.KrdsPickerBox
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.Loading
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.SecondaryButton
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.tr

/**
 * 학교생활 (iPhone·iPad and the Android app's host activity): 시간표·보강, 연락, 질문(방문 신청), 1:1 대화,
 * 소속, 알림. Server: routes/school_*.py. Wording follows the app's 해요체; short buttons keep 수정·삭제·제출.
 */

private fun messageOf(e: Throwable) = e.message ?: tr("인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요.")
/** 날짜 틀의 {MON}·{DAY} (영어 "OCT 12"): "2026-10-12" → MON=OCT, DAY=12. */
private fun monDay(iso: String): Array<Pair<String, Any?>> {
    val m = iso.substring(5, 7).toIntOrNull() ?: return emptyArray()
    return arrayOf("MON" to kr.co.gcflarchive.core.i18n.I18nDates.MONTHS[(m - 1).coerceIn(0, 11)], "DAY" to iso.substring(8, 10).toIntOrNull())
}

private fun time(iso: String) = if (iso.isBlank()) "" else CommunityParser.displayTime(iso, Clock.System.now())
private val SEOUL = TimeZone.of("Asia/Seoul")

enum class SchoolTab(val label: String) {
    TIMETABLE("시간표"), INBOX("연락함"), SEND("연락 보내기"), ASK("선생님께 질문"), VISITS("질문함"), THREADS("대화"), GROUPS("소속·명단"), ALERTS("알림");

    companion object {
        fun forRole(role: SchoolRole) = if (role == SchoolRole.TEACHER) listOf(TIMETABLE, VISITS, SEND, THREADS, GROUPS, ALERTS)
        else listOf(TIMETABLE, INBOX, ASK, THREADS, ALERTS)
    }
}

/** Opens what an alert points at. */
fun AppState.openSchoolTarget(target: String, role: SchoolRole) {
    val t = parseTarget(target)
    when (t.kind) {
        TargetKind.MESSAGE -> t.id?.let { push(Route.SchoolMessage(it)) }
        TargetKind.THREAD -> t.id?.let { push(Route.SchoolThread(it)) }
        TargetKind.VISIT -> t.id?.let { push(Route.SchoolVisit(it)) }
        TargetKind.SUBSTITUTE, TargetKind.TIMETABLE -> push(Route.School(SchoolTab.TIMETABLE.name))
        TargetKind.NONE -> push(Route.School(SchoolTab.ALERTS.name))
    }
}

@Composable
private fun <T> Loaded(
    key: Any?,
    load: suspend () -> T,
    content: @Composable (T, reload: () -> Unit) -> Unit,
) {
    val app = LocalApp.current
    var data by remember(key) { mutableStateOf<Result<T>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(key, tick, app.sessionVersion, app.contentVersion) { data = runCatching { load() } }
    when (val r = data) {
        null -> Loading()
        else -> r.fold(
            onFailure = { e ->
                if (e is LoginRequiredException) StateMessage(tr("로그인하면 볼 수 있어요."), null, tr("로그인하기"), { app.requireLogin() }, locked = true)
                else StateMessage(tr("불러오지 못했어요."), messageOf(e), tr("다시 불러오기"), { tick++ })
            },
            onSuccess = { content(it) { tick++ } },
        )
    }
}

@Composable
private fun Scroll(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, max: Int, lines: Int = 1, placeholder: String = "") {
    OutlinedTextField(
        value = value, onValueChange = { if (it.length <= max) onChange(it) },
        label = { Text(label) }, placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
        singleLine = lines == 1, minLines = lines, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Row2(content: @Composable () -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }

// ── 학교생활 홈 (탭) ─────────────────────────────────────────────────────────

@Composable
fun SchoolScreen(startTab: String? = null) {
    val app = LocalApp.current
    ScreenScaffold(tr("학교생활")) {
        Loaded(Unit, { app.services.school.me() }) { me, _ ->
            if (!me.loggedIn) {
                StateMessage(tr("로그인하면 시간표와 연락을 볼 수 있어요."), null, tr("로그인하기"), { app.requireLogin() }, locked = true)
                return@Loaded
            }
            if (me.role == SchoolRole.OTHER) {
                StateMessage(tr("학생·선생님 계정으로 로그인하면 쓸 수 있어요."))
                return@Loaded
            }
            val tabs = SchoolTab.forRole(me.role)
            var tab by remember { mutableStateOf(tabs.firstOrNull { it.name == startTab } ?: SchoolTab.TIMETABLE) }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tabs.forEach { t ->
                        val count = when (t) {
                            SchoolTab.INBOX -> me.unreadMessages
                            SchoolTab.VISITS -> me.pendingVisits
                            SchoolTab.ALERTS -> me.unreadNotifications
                            else -> 0
                        }
                        KrdsChip(if (count > 0) "${tr(t.label)} $count" else tr(t.label), tab == t) { tab = t }
                    }
                }
                Hairline()
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        SchoolTab.TIMETABLE -> TimetableTab(me)
                        SchoolTab.INBOX -> InboxTab()
                        SchoolTab.SEND -> SendTab()
                        SchoolTab.ASK -> AskTab()
                        SchoolTab.VISITS -> VisitsTab()
                        SchoolTab.THREADS -> ThreadsTab(me)
                        SchoolTab.GROUPS -> GroupsTab()
                        SchoolTab.ALERTS -> AlertsTab(me)
                    }
                }
            }
        }
    }
}

// ── 시간표 ──────────────────────────────────────────────────────────────────

@Composable
private fun TimetableTab(me: SchoolMe) {
    val app = LocalApp.current
    // 시간표 보기: "" = 내 시간표, "c:2-3" = 반별, "t:<교사ID>" = 교사별 (학생은 공개된 선생님만).
    var view by remember { mutableStateOf("") }
    var subPanel by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val teacher = me.role == SchoolRole.TEACHER
    var choices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        val classes = runCatching { app.services.school.classes() }.getOrDefault(emptyList())
        val teachers = runCatching { app.services.school.teachers("") }.getOrDefault(emptyList()).sortedBy { it.name }
        choices = classes.map { "c:${it.grade}-${it.klass}" to it.label } +
            teachers.map { "t:${it.teacherId}" to tr("{name} 시간표", "name" to teacherLabel(it.name)) }
    }
    // Always this week (on weekends the server already gives the coming week), as one 5×7 table.
    Loaded(view to me.role, {
        val week = when {
            view.startsWith("c:") -> view.drop(2).split("-").let { (g, k) -> app.services.school.classWeek(ClassRef(g.toInt(), k.toInt()), null) }
            view.startsWith("t:") -> app.services.school.teacherWeek(view.drop(2), null)
            else -> app.services.school.myWeek(null)
        }
        val subs = if (teacher && view.isEmpty()) runCatching { app.services.school.substitutes() }.getOrNull() else null
        week to subs
    }) { (week, subs), reload ->
        var picked by remember(week) { mutableStateOf<Pair<Int, Int>?>(null) }
        Scroll {
            Text(week.title, style = KrdsText.sectionTitle)
            if (choices.isNotEmpty()) {
                KrdsDropdown(tr("시간표 보기"), listOf("" to tr("내 시간표")) + choices, view, { view = it; subPanel = null })
            }
            if (!week.hasTimetable) InfoBox(tr("아직 시간표가 없어요. 관리자가 시간표를 올리면 여기에 보여요."))
            // 고른 칸의 자세한 내용: 표 바로 위에 (표를 내리지 않아도 보이게).
            val sel = picked
            if (sel == null) Text(tr("칸을 누르면 과목 이름과 장소를 자세히 볼 수 있어요."), style = KrdsText.meta)
            else {
                val (d, pi) = sel
                val p = week.classPeriods[pi]
                val c = week.cell(d, p)
                val mine = teacher && view.isEmpty()
                // 학생: 공개된 선생님이면 '이 선생님께 연락하기' (선생님께 질문 화면으로).
                val contactId = if (!teacher && !c.empty) c.teacherIds.firstOrNull() else null
                PeriodRow("${week.days[d].weekday} ${p.label}", "${p.start}~${p.end}", c,
                    onSub = if (mine && !c.empty && !c.handedOver) ({ subPanel = week.days[d].date to p.key.toInt() }) else null,
                    onContact = contactId?.let { id -> { app.push(Route.SchoolAsk(id)) } })
                // 체육처럼 장소가 매번 바뀌는 수업: 선생님이 그날 장소를 정해요.
                if (mine && c.placeSubject && !c.handedOver && week.days[d].date >= Clock.System.todayIn(SEOUL).toString()) {
                    PlacePicker(week.days[d].date, p.key.toInt(), if (c.placeSet) c.room else "", onSaved = reload)
                }
            }
            WeekGrid(week, picked) { picked = if (picked == it) null else it }
            subPanel?.let { (d, per) -> SubstitutePanel(d, per, onClose = { subPanel = null }, onSent = { reload() }) }
            if (subs != null) SubstituteLists(subs, onChanged = reload)
            Box(Modifier.heightIn(min = 24.dp))
        }
    }
}

/**
 * 월~금 × 1~7교시 in one table, TDS style: grey rounded tiles with small gaps on a white card; blue only
 * marks 오늘 and the picked cell, red a changed class. Tapping a cell picks (day, period index).
 */
@Composable
private fun WeekGrid(week: TtWeek, picked: Pair<Int, Int>?, onPick: (Pair<Int, Int>) -> Unit) =
    // 설정 > 시간표 과목 이름: 줄임말(심F) or 전체 이름(심화영어 Ⅱ).
    WeekGridContent(week, picked, TimetableNames.full(LocalApp.current.services.prefs), onPick)

@Composable
internal fun WeekGridContent(week: TtWeek, picked: Pair<Int, Int>?, fullNames: Boolean, onPick: (Pair<Int, Int>) -> Unit) {
    val k = Krds.colors
    val periods = week.classPeriods
    val gap = 4.dp
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(k.surface).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.width(22.dp))
            week.days.forEach { d ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (d.isToday) k.primaryTint else Color.Transparent).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(d.weekday, style = KrdsText.label.copy(fontWeight = FontWeight.Bold, color = if (d.isToday) k.primaryText else k.textStrong))
                    Text(d.date.takeLast(5).replace('-', '/').trimStart('0'), style = KrdsText.label.copy(fontSize = 11.sp, color = if (d.isToday) k.primaryText else k.textSecondary))
                }
            }
        }
        periods.forEachIndexed { pi, p ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(p.key, style = KrdsText.label.copy(fontWeight = FontWeight.Bold, color = k.textSecondary))
                }
                week.days.forEachIndexed { di, d ->
                    val c = week.cell(di, p)
                    val isPicked = picked == (di to pi)
                    val bg = when {
                        isPicked -> k.primary
                        c.empty -> k.surfaceRaised.copy(alpha = 0.5f)
                        else -> k.surfaceRaised
                    }
                    val fg = if (isPicked) k.onPrimary else k.textStrong
                    val sub = if (isPicked) k.onPrimary else k.textSecondary
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp).clip(shape)
                            .background(bg)
                            .then(if (c.changed && !isPicked) Modifier.border(1.5.dp, k.danger, shape) else Modifier)
                            .clickable(onClickLabel = tr("자세히 보기")) { onPick(di to pi) }
                            .semantics { contentDescription = "${d.weekday} ${p.label} " + (if (c.empty) tr("수업 없음") else c.title) + (if (c.moving) ", " + tr("이동수업") else "") },
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (!c.empty) {
                                // 교사 시간표(내 것·교사별): 학급(2-8)을 크게, 과목은 작게. 학생·반별: 과목을 크게.
                                val teacherWeek = week.kind == "teacher" && c.place.isNotBlank()
                                val subject = c.shortOrFull(fullNames)
                                val name = if (teacherWeek) c.place else subject
                                val big = teacherWeek || name == c.subject
                                Text(name, maxLines = if (big) 1 else 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                    style = KrdsText.label.copy(fontSize = if (big) 14.sp else 11.sp, lineHeight = 13.sp,
                                        fontWeight = FontWeight.Bold, color = fg,
                                        textDecoration = if (c.handedOver) TextDecoration.LineThrough else null))
                                val small = if (teacherWeek) subject else c.place.ifBlank { c.teachers.firstOrNull()?.let(::teacherLabel).orEmpty() }
                                if (small.isNotBlank()) Text(small, maxLines = if (teacherWeek && small != c.subject) 2 else 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center, style = KrdsText.label.copy(fontSize = 11.sp, lineHeight = 13.sp, color = sub))
                            }
                        }
                        // 이동수업: a small blue dot in the corner.
                        if (c.moving && !c.empty) Box(
                            Modifier.align(Alignment.TopEnd).padding(5.dp).size(6.dp)
                                .background(if (isPicked) k.onPrimary else k.primary, RoundedCornerShape(50)),
                        )
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(k.primary, RoundedCornerShape(50)))
        Text(tr("이동수업"), style = KrdsText.label)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(14.dp).border(1.5.dp, k.danger, RoundedCornerShape(4.dp)))
        Text(tr("바뀐 수업"), style = KrdsText.label)
    }
}

@Composable
private fun PeriodRow(label: String, time: String, c: TtCell, onSub: (() -> Unit)?, onContact: (() -> Unit)? = null) {
    KrdsCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.widthIn(min = 64.dp)) {
                Text(label, style = KrdsText.cardTitle)
                Text(time, style = KrdsText.label)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (c.empty) Text("—", style = KrdsText.meta)
                else {
                    Text(c.title, style = KrdsText.cardTitle.copy(textDecoration = if (c.handedOver) TextDecoration.LineThrough else null))
                    if (c.trackLabel.isNotBlank()) Text(c.trackLabel, style = KrdsText.meta)
                    if (c.detail.isNotBlank()) Text(c.detail, style = KrdsText.meta)
                    Row2 {
                        if (c.moving) Badge(tr("이동"), BadgeTone.WARNING)
                        if (c.changed) Badge(tr("변경"), BadgeTone.DANGER)
                    }
                    if (c.note.isNotBlank()) Text(c.note, style = KrdsText.meta)
                }
            }
            if (onSub != null) TextButton(onClick = onSub) { Text(tr("보강 부탁"), color = Krds.colors.primaryText) }
            if (onContact != null) {
                Row(
                    Modifier.align(Alignment.CenterVertically).widthIn(max = 132.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onContact).padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("이 선생님께 연락하기"), style = KrdsText.label.copy(fontWeight = FontWeight.Bold, color = Krds.colors.primaryText),
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Filled.ChevronRight, null, tint = Krds.colors.primaryText)
                }
            }
        }
        if (c.placeSubject && !c.placeSet && !c.empty) Text(tr("장소는 아직 안내 전이에요. 수업 전 쉬는 시간에 알림이 가요."), style = KrdsText.meta,
            modifier = Modifier.padding(top = 8.dp))
    }
}

/** 체육 등: 그날 그 교시 수업 장소. 고르면 바로 저장되고, 학생에게는 수업 전 쉬는 시간 알림에 장소가 가요. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlacePicker(date: String, period: Int, current: String, onSaved: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val presets = listOf("체육관", "운동장", "교실")
    var custom by remember(current) { mutableStateOf(if (current.isNotBlank() && current !in presets) current else "") }
    fun save(place: String) {
        busy = true
        scope.launch {
            runCatching { app.services.school.setClassPlace(date, period, place) }
                .onSuccess { app.toast(if (place.isBlank()) tr("장소를 지웠어요.") else tr("장소를 '{place}'(으)로 정했어요.", "place" to place)); onSaved() }
                .onFailure { app.toast(messageOf(it)) }
            busy = false
        }
    }
    KrdsCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("수업 장소"), style = KrdsText.cardTitle)
            Text(if (current.isBlank()) tr("아직 정하지 않았어요. 정하면 수업 전 쉬는 시간에 학생들에게 장소를 알려요.")
                 else tr("지금 장소: {place}", "place" to current), style = KrdsText.meta)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { p -> KrdsChip(tr(p), current == p) { if (!busy) save(if (current == p) "" else p) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Field(tr("직접 입력"), custom, { custom = it }, 40, placeholder = tr("예: 다목적실")) }
                PrimaryButton(tr("저장"), { save(custom.trim()) }, Modifier.padding(start = 8.dp), enabled = !busy && custom.isNotBlank())
            }
        }
    }
}

@Composable
private fun SubstitutePanel(date: String, period: Int, onClose: () -> Unit, onSent: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    KrdsCard {
        Loaded(date to period, { app.services.school.freeTeachers(date, period) }) { free: FreeTeachers, reload ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("{date} {p}교시 · {what} 보강 부탁", "date" to date.takeLast(5).replace('-', '/'), "p" to period, "what" to free.myClasses.joinToString(", "), *monDay(date)), style = KrdsText.cardTitle, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("닫기")) }
                }
                Field(tr("전할 말 (선택)"), note, { note = it }, 300, placeholder = tr("예: 출장이라 자습 지도 부탁드려요"))
                if (free.teachers.isEmpty()) Text(tr("그 시간에 수업이 없는 선생님이 없어요."), style = KrdsText.meta)
                free.teachers.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(teacherLabel(t.name), style = KrdsText.body.copy(fontWeight = FontWeight.Bold))
                            if (t.department.isNotBlank()) Text(t.department, style = KrdsText.meta)
                        }
                        if (t.asked) Badge(tr("답을 기다려요"), BadgeTone.NEUTRAL)
                        else PrimaryButton(tr("부탁"), {
                            busy = true
                            scope.launch {
                                runCatching { app.services.school.askSubstitute(date, period, t.teacherId, note.trim()) }
                                    .onSuccess { app.toast(tr("보강을 부탁했어요. 수락하면 알려드려요.")); reload(); onSent() }
                                    .onFailure { app.toast(messageOf(it)) }
                                busy = false
                            }
                        }, enabled = !busy)
                    }
                    Hairline()
                }
            }
        }
    }
}

@Composable
private fun SubstituteLists(subs: SubRequests, onChanged: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    fun act(id: Int, action: String, done: String) = scope.launch {
        runCatching { app.services.school.substituteAction(id, action) }
            .onSuccess { app.toast(done); app.contentChanged(); onChanged() }
            .onFailure { app.toast(messageOf(it)) }
    }
    Text(tr("보강 부탁"), style = KrdsText.sectionTitle, modifier = Modifier.padding(top = 8.dp))
    Text(tr("내 수업 칸의 '보강 부탁'으로 그 시간에 수업이 없는 선생님께 부탁할 수 있어요."), style = KrdsText.meta)
    @Composable
    fun row(s: SubRequest, received: Boolean) = KrdsCard {
        Row2 {
            Badge(tr(SUB_STATUS[s.status] ?: s.status), if (s.status == "accepted") BadgeTone.SUCCESS else if (s.status == "pending") BadgeTone.INFO else BadgeTone.NEUTRAL)
            Text(tr("{date}({wd}) {p}교시", "date" to s.date.takeLast(5).replace('-', '/'), "wd" to s.weekday, "p" to s.period, *monDay(s.date)), style = KrdsText.cardTitle)
        }
        Text(if (received) tr("{name} 선생님이 부탁했어요", "name" to s.requesterName) else tr("{name} 선생님께 부탁했어요", "name" to s.candidateName), style = KrdsText.meta)
        if (s.note.isNotBlank()) Text(s.note, style = KrdsText.body)
        Row2 {
            if (received && s.status == "pending") {
                PrimaryButton(tr("수락"), { act(s.id, "accept", tr("수락했어요. 시간표에 보강이 들어갔어요.")) })
                SecondaryButton(tr("거절"), { act(s.id, "decline", tr("거절했어요.")) })
            } else if (s.status == "pending" || s.status == "accepted") {
                SecondaryButton(tr("취소"), { act(s.id, "cancel", tr("취소했어요.")) })
            }
        }
    }
    Text(tr("받은 부탁"), style = KrdsText.cardTitle)
    if (subs.received.isEmpty()) Text(tr("받은 부탁이 없어요."), style = KrdsText.meta)
    subs.received.forEach { row(it, true) }
    Text(tr("보낸 부탁"), style = KrdsText.cardTitle)
    if (subs.sent.isEmpty()) Text(tr("보낸 부탁이 없어요."), style = KrdsText.meta)
    subs.sent.forEach { row(it, false) }
}

// ── 연락함 · 연락 보내기 ────────────────────────────────────────────────────

@Composable
private fun MessageRow(m: SchoolMessage, teacher: Boolean, onClick: () -> Unit) {
    KrdsCard(onClick = onClick) {
        Row2 {
            if (!teacher && !m.read) Badge(tr("새 연락"), BadgeTone.DANGER)
            Text(m.title, style = KrdsText.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val meta = if (teacher) "${m.audience} · ${tr("읽음")} ${m.readCount}${if (m.byClass) "" else "/${m.recipientCount}"} · ${time(m.createdAt)}"
        else "${teacherLabel(m.senderName)} · ${time(m.createdAt)}${if (m.fileCount > 0) " · " + tr("파일 {n}개", "n" to m.fileCount) else ""}"
        Text(meta, style = KrdsText.meta)
    }
}

@Composable
private fun InboxTab() {
    val app = LocalApp.current
    Loaded(Unit, { app.services.school.messages() }) { list, _ ->
        Scroll {
            if (list.isEmpty()) Text(tr("아직 받은 연락이 없어요."), style = KrdsText.meta)
            list.forEach { m -> MessageRow(m, false) { app.push(Route.SchoolMessage(m.id)) } }
        }
    }
}

@Composable
private fun SendTab() {
    val app = LocalApp.current
    Loaded(Unit, { app.services.school.messages() }) { list, _ ->
        Scroll {
            PrimaryButton(tr("새 연락 보내기"), { app.push(Route.SchoolCompose) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            Text(tr("보낸 연락"), style = KrdsText.sectionTitle)
            if (list.isEmpty()) Text(tr("아직 보낸 연락이 없어요."), style = KrdsText.meta)
            list.forEach { m -> MessageRow(m, true) { app.push(Route.SchoolMessage(m.id)) } }
        }
    }
}

@Composable
private fun FileChips(files: List<SchoolFile>) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    files.forEach { f ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, Krds.colors.borderStrong, RoundedCornerShape(8.dp))
            .clickable(onClickLabel = tr("{name} 내려받기", "name" to f.name)) {
                scope.launch {
                    runCatching { saveFile(f.name, app.services.site.bytes(f.url), null) }
                        .onSuccess { app.toast(tr("{name}을(를) 저장했어요.", "name" to f.name)) }
                        .onFailure { app.toast(messageOf(it)) }
                }
            }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AttachFile, null, tint = Krds.colors.primaryText)
            Text(f.name, style = KrdsText.body, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(f.sizeLabel, style = KrdsText.meta)
        }
    }
}

/** Picks files and uploads each to /api/school/files; [ids] are what to send. */
private class FileAttach {
    val files = mutableStateListOf<SchoolFile>()
    val ids: List<String> get() = files.map { it.id }
}

@Composable
private fun FilePickerRow(state: FileAttach) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val pick = rememberFilePicker { picked: List<UploadFile> ->
        scope.launch {
            busy = true
            for (f in picked) {
                if (state.files.size >= 5) { app.toast(tr("파일은 5개까지 붙일 수 있어요.")); break }
                runCatching { app.services.school.uploadFile(f) }.onSuccess { state.files += it }.onFailure { app.toast(messageOf(it)) }
            }
            busy = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row2 {
            SecondaryButton(if (busy) tr("올리고 있어요") else tr("파일 추가"), pick, enabled = !busy, icon = Icons.Filled.AttachFile)
            Text(tr("5개, 하나에 20MB까지"), style = KrdsText.meta)
        }
        state.files.toList().forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📎 ${f.name}", style = KrdsText.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { state.files.remove(f) }) { Icon(Icons.Filled.Close, tr("{name} 빼기", "name" to f.name)) }
            }
        }
    }
}

@Composable
fun SchoolMessageScreen(id: Int) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    ScreenScaffold(tr("연락")) {
        Loaded(id, { app.services.school.message(id) }) { m: MessageDetail, _ ->
            var reply by remember { mutableStateOf("") }
            val files = remember { FileAttach() }
            var busy by remember { mutableStateOf(false) }
            Scroll {
                Text(m.summary.title, style = KrdsText.pageTitle)
                Text("${teacherLabel(m.summary.senderName)} · ${time(m.summary.createdAt)}", style = KrdsText.meta)
                Text(tr("받는 사람: {who}", "who" to m.summary.audience), style = KrdsText.meta)
                Hairline()
                if (m.body.isNotBlank()) Text(m.body, style = KrdsText.body)
                FileChips(m.files)
                val readers = m.readers
                if (readers != null) {
                    KrdsCard {
                        Text(tr("읽음 {n}명", "n" to readers.size), style = KrdsText.cardTitle)
                        Text(if (readers.isEmpty()) tr("아직 읽은 사람이 없어요.") else readers.joinToString(", "), style = KrdsText.body)
                        if (m.unread.isNotEmpty()) Text(tr("안 읽음: {list}", "list" to m.unread.joinToString(", ")), style = KrdsText.meta)
                        if (m.summary.byClass) Text(tr("학급·학년으로 보낸 연락은 학생이 열어본 뒤에 읽음 명단에 들어가요."), style = KrdsText.meta)
                    }
                } else if (m.summary.allowReply) {
                    if (m.threadId != null) SecondaryButton(tr("답장한 대화 보기"), { app.push(Route.SchoolThread(m.threadId!!)) }, Modifier.fillMaxWidth())
                    else {
                        Text(tr("답장 ({name} 선생님에게만 보여요)", "name" to m.summary.senderName), style = KrdsText.cardTitle)
                        Field(tr("답장"), reply, { reply = it }, 5000, lines = 3)
                        FilePickerRow(files)
                        PrimaryButton(tr("보내기"), {
                            busy = true
                            scope.launch {
                                runCatching { app.services.school.reply(id, reply.trim(), files.ids) }
                                    .onSuccess { app.toast(tr("답장을 보냈어요.")); app.pop(); app.push(Route.SchoolThread(it)) }
                                    .onFailure { app.toast(messageOf(it)) }
                                busy = false
                            }
                        }, Modifier.fillMaxWidth(), enabled = !busy && (reply.isNotBlank() || files.files.isNotEmpty()))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolComposeScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    ScreenScaffold(tr("새 연락")) {
        Loaded(Unit, { app.services.school.classes() to app.services.school.groups() }) { (classes, groups), _ ->
            val grades = remember { mutableStateListOf<Int>() }
            val pickedClasses = remember { mutableStateListOf<String>() }
            val pickedGroups = remember { mutableStateListOf<Int>() }
            var hakbuns by remember { mutableStateOf("") }
            var title by remember { mutableStateOf("") }
            var body by remember { mutableStateOf("") }
            var allowReply by remember { mutableStateOf(true) }
            var busy by remember { mutableStateOf(false) }
            val files = remember { FileAttach() }
            Scroll {
                Text(tr("받는 사람"), style = KrdsText.sectionTitle)
                val gradeList = classes.map { it.grade }.distinct()
                if (gradeList.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    gradeList.forEach { g -> KrdsChip(tr("{g}학년 전체", "g" to g), g in grades) { if (g in grades) grades.remove(g) else grades.add(g) } }
                }
                if (classes.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    classes.forEach { c -> KrdsChip(c.key, c.key in pickedClasses) { if (c.key in pickedClasses) pickedClasses.remove(c.key) else pickedClasses.add(c.key) } }
                } else Text(tr("시간표가 올라오면 학급을 고를 수 있어요. 지금은 학번을 직접 적어주세요."), style = KrdsText.meta)
                if (groups.groups.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.groups.forEach { g ->
                        KrdsChip("${g.name} (${tr("{n}명", "n" to g.memberCount)})", g.id in pickedGroups) { if (g.id in pickedGroups) pickedGroups.remove(g.id) else pickedGroups.add(g.id) }
                    }
                }
                Field(tr("학번 직접 입력 (띄어쓰기로 구분)"), hakbuns, { hakbuns = it }, 2000, placeholder = tr("예: 20315 20301"))
                Hairline()
                Field(tr("제목"), title, { title = it }, 120)
                Field(tr("내용"), body, { body = it }, 5000, lines = 6)
                FilePickerRow(files)
                Row(Modifier.fillMaxWidth().toggleable(allowReply, role = Role.Switch) { allowReply = it }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("학생 답장 받기"), style = KrdsText.body)
                        Text(tr("답장은 선생님에게만 보여요."), style = KrdsText.meta)
                    }
                    Switch(allowReply, null, colors = SwitchDefaults.colors(checkedTrackColor = Krds.colors.primary))
                }
                val audience = Audience(grades.toList(), pickedClasses.toList(), pickedGroups.toList(), hakbuns)
                PrimaryButton(tr("보내기"), {
                    busy = true
                    scope.launch {
                        runCatching { app.services.school.sendMessage(title.trim(), body.trim(), audience, files.ids, allowReply) }
                            .onSuccess { app.toast(tr("연락을 보냈어요.")); app.contentChanged(); app.pop() }
                            .onFailure { app.toast(messageOf(it)) }
                        busy = false
                    }
                }, Modifier.fillMaxWidth(), enabled = !busy && title.isNotBlank() && !audience.isEmpty)
            }
        }
    }
}

// ── 대화 ────────────────────────────────────────────────────────────────────

@Composable
private fun ThreadsTab(me: SchoolMe) {
    val app = LocalApp.current
    Loaded(Unit, { app.services.school.threads() }) { list: List<ThreadSummary>, _ ->
        Scroll {
            Text(if (me.role == SchoolRole.TEACHER) tr("학생 답장과 질문 답변이 1:1 대화로 모여요.") else tr("선생님과 나눈 1:1 대화예요."), style = KrdsText.meta)
            if (list.isEmpty()) Text(tr("아직 대화가 없어요."), style = KrdsText.meta)
            list.forEach { t ->
                KrdsCard(onClick = { app.push(Route.SchoolThread(t.id)) }) {
                    Row2 {
                        if (t.unread) Badge(tr("새 글"), BadgeTone.DANGER)
                        Text(t.subject, style = KrdsText.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val who = if (me.role == SchoolRole.TEACHER) tr("{who} 학생", "who" to t.studentKey) else teacherLabel(t.teacherName)
                    Text("$who · ${t.lastPost}", style = KrdsText.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun SchoolThreadScreen(id: Int) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    ScreenScaffold(tr("대화")) {
        Loaded(id, { app.services.school.thread(id) }) { t: ThreadDetail, reload ->
            var text by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            val files = remember { FileAttach() }
            Scroll {
                Text(t.summary.subject, style = KrdsText.sectionTitle)
                t.posts.forEach { p ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (p.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                        Column(
                            Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (p.mine) Krds.colors.infoBg else Krds.colors.surface)
                                .border(1.dp, Krds.colors.border, RoundedCornerShape(12.dp)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val author = if (p.authorName == t.summary.teacherName) teacherLabel(p.authorName) else p.authorName
                            Text("${if (p.mine) tr("나") else author} · ${time(p.createdAt)}", style = KrdsText.label)
                            if (p.body.isNotBlank()) Text(p.body, style = KrdsText.body)
                            FileChips(p.files)
                        }
                    }
                }
                Hairline()
                Field(tr("보내기"), text, { text = it }, 5000, lines = 3)
                FilePickerRow(files)
                PrimaryButton(tr("보내기"), {
                    busy = true
                    scope.launch {
                        runCatching { app.services.school.post(id, text.trim(), files.ids) }
                            .onSuccess { text = ""; files.files.clear(); reload() }
                            .onFailure { app.toast(messageOf(it)) }
                        busy = false
                    }
                }, Modifier.fillMaxWidth(), enabled = !busy && (text.isNotBlank() || files.files.isNotEmpty()))
            }
        }
    }
}

// ── 선생님께 질문 (학생) ────────────────────────────────────────────────────

@Composable
private fun VisitRow(v: Visit, teacher: Boolean, onClick: () -> Unit) {
    KrdsCard(onClick = onClick) {
        Row2 {
            Badge(tr(VISIT_STATUS[v.status] ?: v.status), when (v.status) { "pending" -> BadgeTone.INFO; "accepted" -> BadgeTone.SUCCESS; "answered" -> BadgeTone.WARNING; else -> BadgeTone.NEUTRAL })
            Text(v.purpose, style = KrdsText.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val who = if (teacher) tr("{who} 학생", "who" to v.studentKey + if (v.studentName.isNotBlank()) " ${v.studentName}" else "") else teacherLabel(v.teacherName)
        Text("$who · ${time(v.createdAt)}", style = KrdsText.meta)
    }
}

@Composable
private fun TeacherSearch(placeholder: String, exclude: String = "", action: String, onPick: (TeacherCard) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TeacherCard>?>(null) }
    fun search() = scope.launch {
        runCatching { app.services.school.teachers(q.trim()) }.onSuccess { results = it.filter { t -> t.teacherId != exclude } }.onFailure { app.toast(messageOf(it)) }
    }
    // No floating label: it adds space above the box, so the button would sit lower than the field.
    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = q, onValueChange = { if (it.length <= 50) q = it }, singleLine = true,
            placeholder = { Text(placeholder.ifBlank { tr("담당 업무·부서·이름") }) },
            modifier = Modifier.weight(1f).semantics { contentDescription = tr("선생님 검색: 담당 업무·부서·이름") },
        )
        PrimaryButton(tr("검색"), { search() }, Modifier.padding(start = 8.dp).fillMaxHeight())
    }
    results?.let { list ->
        if (list.isEmpty()) Text(if (q.isBlank()) tr("선생님 명단이 아직 없어요.") else tr("찾는 선생님이 없어요. 다른 말로 검색해보세요."), style = KrdsText.meta)
        list.take(30).forEach { t ->
            KrdsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(teacherLabel(t.name), style = KrdsText.cardTitle)
                        Text(listOf(t.department, t.location).filter { it.isNotBlank() }.joinToString(" · "), style = KrdsText.meta)
                        if (t.duties.isNotEmpty()) Text(tr("담당: {list}", "list" to t.duties.joinToString(", ")), style = KrdsText.meta)
                    }
                    PrimaryButton(action, { onPick(t) })
                }
            }
        }
    }
}

@Composable
private fun AskTab() {
    val app = LocalApp.current
    Loaded(Unit, { app.services.school.visits() }) { mine, _ ->
        Scroll {
            Text(tr("담당 업무로 선생님을 찾아 질문하고, 찾아갈 시간을 1~3지망까지 골라요."), style = KrdsText.meta)
            TeacherSearch(tr("담당 업무·부서·이름 (예: 방과후)"), action = tr("질문하기")) { app.push(Route.SchoolAsk(it.teacherId)) }
            Text(tr("내 질문"), style = KrdsText.sectionTitle, modifier = Modifier.padding(top = 8.dp))
            if (mine.isEmpty()) Text(tr("아직 질문이 없어요."), style = KrdsText.meta)
            mine.forEach { v -> VisitRow(v, false) { app.push(Route.SchoolVisit(v.id)) } }
        }
    }
}

@Composable
fun SchoolAskScreen(teacherId: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    ScreenScaffold(tr("선생님께 질문")) {
        Loaded(teacherId, { app.services.school.visitOptions(teacherId) }) { first: VisitOptions, _ ->
            var purpose by remember { mutableStateOf("") }
            var name by remember { mutableStateOf("") }
            val dates = remember { mutableStateListOf(first.dates.firstOrNull() ?: first.defaultDate, "", "") }
            val periods = remember { mutableStateListOf("", "", "") }
            var busy by remember { mutableStateOf(false) }
            // 1·2지망은 꼭, 3지망은 골라도 되고 안 골라도 돼요.
            val ready = purpose.isNotBlank() && (0..1).all { dates[it].isNotBlank() && periods[it].isNotBlank() }
            Scroll {
                KrdsCard {
                    Text(teacherLabel(first.teacher.name), style = KrdsText.cardTitle)
                    Text(listOf(first.teacher.department, first.teacher.location).filter { it.isNotBlank() }.joinToString(" · "), style = KrdsText.meta)
                }
                Field(tr("용건"), purpose, { purpose = it }, 500, lines = 3, placeholder = tr("용건(필수)"))
                Field(tr("이름 (선택)"), name, { name = it }, 30, placeholder = tr("내가 누구인지(비워두면 학번만 보여요)"))
                Text(tr("찾아갈 날짜와 시간"), style = KrdsText.sectionTitle)
                Text(tr("1·2지망은 꼭 골라주세요. 선생님 수업이 없는 시간만 보여요."), style = KrdsText.meta)
                (0..2).forEach { i -> SlotPicker(i, teacherId, first.dates, dates[i], periods[i], { dates[i] = it; periods[i] = "" }, { periods[i] = it }) }
                PrimaryButton(tr("제출"), {
                    busy = true
                    scope.launch {
                        runCatching { app.services.school.askVisit(teacherId, purpose.trim(), name.trim(), (0..2).map { dates[it] to periods[it] }) }
                            .onSuccess { app.toast(tr("질문을 보냈어요. 선생님이 답하면 알려드려요.")); app.contentChanged(); app.pop(); app.push(Route.SchoolVisit(it.id)) }
                            .onFailure { app.toast(messageOf(it)) }
                        busy = false
                    }
                }, Modifier.fillMaxWidth(), enabled = !busy && ready)
            }
        }
    }
}

/** One 지망: '날짜' (opens [VisitCalendar]) and '시간' side by side, same look. */
@Composable
private fun SlotPicker(
    index: Int, teacherId: String, allowed: List<String>, date: String, period: String,
    onDate: (String) -> Unit, onPeriod: (String) -> Unit,
) {
    val app = LocalApp.current
    var calendar by remember { mutableStateOf(false) }
    val today = remember { Clock.System.todayIn(SEOUL) }
    var options by remember(date) { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(date) {
        options = if (date.isBlank()) emptyList()
        else runCatching { app.services.school.visitOptions(teacherId, date).options.map { it.period to it.label } }.getOrDefault(emptyList())
    }
    KrdsCard {
        Text(tr("{n}지망", "n" to index + 1) + if (index == 2) " " + tr("(선택)") else "", style = KrdsText.cardTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KrdsPickerBox(tr("날짜"), if (date.isBlank()) tr("고르기") else visitDayText(date, today), Modifier.weight(1f)) { calendar = true }
            val o = options
            Box(Modifier.weight(1f)) {
                when {
                    date.isBlank() -> KrdsPickerBox(tr("시간"), tr("날짜 먼저")) { calendar = true }
                    o == null -> KrdsPickerBox(tr("시간"), tr("불러오는 중")) {}
                    o.isEmpty() -> KrdsPickerBox(tr("시간"), tr("고를 시간 없음")) { calendar = true }
                    else -> KrdsDropdown(tr("시간"), listOf("" to tr("고르기")) + o, period, onPeriod)
                }
            }
        }
        if (index == 2 && date.isNotBlank()) {
            TextButton(onClick = { onDate("") }) { Text(tr("3지망 지우기"), color = Krds.colors.primaryText) }
        }
    }
    if (calendar) VisitCalendar(allowed.toSet(), date, today, onDismiss = { calendar = false }) { onDate(it); calendar = false }
}

/** 월~금 달력 (이번 주부터 4주). 지난 날, 3주 넘는 날 등 [allowed]에 없는 날은 누를 수 없어요. */
@Composable
private fun VisitCalendar(allowed: Set<String>, selected: String, today: LocalDate, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("닫기"), color = Krds.colors.primaryText) } },
        title = { Text(tr("날짜 고르기"), style = KrdsText.sectionTitle) },
        containerColor = Krds.colors.surface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { listOf(tr("월"), tr("화"), tr("수"), tr("목"), tr("금")).forEach { Text(it, style = KrdsText.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
                (0..3).forEach { w ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..4).forEach { i ->
                            val d = monday.plus(DatePeriod(days = w * 7 + i))
                            val iso = d.toString()
                            val ok = iso in allowed
                            val picked = iso == selected
                            val isToday = d == today
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (picked) Krds.colors.primary else Color.Transparent)
                                    .then(if (isToday && !picked) Modifier.border(1.5.dp, Krds.colors.primary, RoundedCornerShape(10.dp)) else Modifier)
                                    .then(if (ok) Modifier.clickable(onClickLabel = tr("고르기")) { onPick(iso) } else Modifier)
                                    .semantics { contentDescription = "${d.monthNumber}/${d.dayOfMonth}" + if (ok) "" else ", " + tr("고를 수 없어요") },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (d.dayOfMonth == 1) "${d.monthNumber}/1" else d.dayOfMonth.toString(),
                                    style = KrdsText.body.copy(
                                        color = when {
                                            picked -> Krds.colors.onPrimary
                                            ok -> Krds.colors.textStrong
                                            else -> Krds.colors.textSecondary.copy(alpha = 0.45f)
                                        },
                                        textDecoration = if (ok) null else TextDecoration.LineThrough,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

// ── 질문 상세 / 질문함 (교사) ───────────────────────────────────────────────

@Composable
private fun VisitsTab() {
    val app = LocalApp.current
    var status by remember { mutableStateOf("pending") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("pending" to tr("답을 기다리는 질문"), "accepted" to tr("방문 수락"), "answered" to tr("답변함"), "" to tr("전체")).forEach { (k, l) -> KrdsChip(l, k == status) { status = k } }
        }
        Loaded(status, { app.services.school.visits(status.ifBlank { null }) }) { list, _ ->
            Scroll {
                if (list.isEmpty()) Text(tr("질문이 없어요."), style = KrdsText.meta)
                list.forEach { v -> VisitRow(v, true) { app.push(Route.SchoolVisit(v.id)) } }
            }
        }
    }
}

@Composable
fun SchoolVisitScreen(id: Int) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    ScreenScaffold(tr("질문")) {
        Loaded(id, { app.services.school.visit(id) to app.services.school.me() }) { (v, me), reload ->
            val teacher = me.role == SchoolRole.TEACHER
            var answer by remember { mutableStateOf("") }
            var accIndex by remember { mutableStateOf(v.acceptedIndex) }
            var accNote by remember { mutableStateOf(v.teacherNote) }
            var fwNote by remember { mutableStateOf("") }
            var fwNotify by remember { mutableStateOf(true) }
            var confirmCancel by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            fun run(done: String, block: suspend () -> Unit, after: () -> Unit = reload) = scope.launch {
                busy = true
                runCatching { block() }.onSuccess { app.toast(done); app.contentChanged(); after() }.onFailure { app.toast(messageOf(it)) }
                busy = false
            }
            Scroll {
                KrdsCard {
                    Row2 { Badge(tr(VISIT_STATUS[v.status] ?: v.status), if (v.status == "accepted") BadgeTone.SUCCESS else BadgeTone.INFO) }
                    Text(if (teacher) tr("{who} 학생", "who" to v.studentKey + if (v.studentName.isNotBlank()) " ${v.studentName}" else "") + " · ${time(v.createdAt)}"
                    else "${teacherLabel(v.teacherName)}${if (v.location.isNotBlank()) " · ${v.location}" else ""}", style = KrdsText.meta)
                    Text(v.purpose, style = KrdsText.body)
                    v.slots.forEachIndexed { i, s ->
                        Text("${tr("{n}지망", "n" to i + 1)} · ${s.label}${if (v.acceptedIndex == i) " ✓ " + tr("수락") else ""}",
                            style = KrdsText.body.copy(fontWeight = if (v.acceptedIndex == i) FontWeight.Bold else FontWeight.Normal))
                    }
                    if (v.teacherNote.isNotBlank()) InfoBox(v.teacherNote)
                    v.forwards.forEach { h ->
                        Text("↪ " + tr("{from} 선생님이 {to} 선생님께 전달했어요", "from" to h.fromName, "to" to h.toName) + (if (teacher && h.note.isNotBlank()) " · ${h.note}" else "") + (if (teacher && !h.notified) " " + tr("(학생에게 알리지 않음)") else ""), style = KrdsText.meta)
                    }
                    v.threadId?.let { tid -> SecondaryButton(tr("답변 대화 보기"), { app.push(Route.SchoolThread(tid)) }) }
                }
                if (!teacher && v.status != "cancelled") {
                    if (!confirmCancel) SecondaryButton(tr("질문 취소"), { confirmCancel = true }, danger = true)
                    else KrdsCard {
                        Text(tr("질문을 취소할까요?"), style = KrdsText.cardTitle)
                        Row2 {
                            SecondaryButton(tr("취소하기"), { run(tr("질문을 취소했어요."), { app.services.school.cancelVisit(id) }) }, danger = true, enabled = !busy)
                            SecondaryButton(tr("그대로 두기"), { confirmCancel = false })
                        }
                    }
                }
                if (teacher && v.status != "cancelled") {
                    KrdsCard {
                        Text(tr("바로 답변"), style = KrdsText.cardTitle)
                        Text(tr("간단한 질문이면 찾아오지 않아도 되게 바로 답해요."), style = KrdsText.meta)
                        Field(tr("답변"), answer, { answer = it }, 5000, lines = 3)
                        PrimaryButton(tr("보내기"), { run(tr("답변을 보냈어요."), { app.services.school.answerVisit(id, answer.trim()) }) }, enabled = !busy && answer.isNotBlank())
                    }
                    KrdsCard {
                        Text(tr("방문 수락"), style = KrdsText.cardTitle)
                        v.slots.forEachIndexed { i, s ->
                            Row(Modifier.fillMaxWidth().selectable(accIndex == i, role = Role.RadioButton) { accIndex = i }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(accIndex == i, null, colors = RadioButtonDefaults.colors(selectedColor = Krds.colors.primary))
                                Text("${tr("{n}지망", "n" to i + 1)} · ${s.label}", style = KrdsText.body, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        Field(tr("전할 말 (선택)"), accNote, { accNote = it }, 500, placeholder = tr("예: 교무실 앞에서 기다려주세요"))
                        PrimaryButton(tr("수락"), { accIndex?.let { idx -> run(tr("수락했어요. 학생에게 알렸어요."), { app.services.school.acceptVisit(id, idx, accNote.trim()) }) } },
                            enabled = !busy && accIndex != null)
                    }
                    KrdsCard {
                        Text(tr("담당자에게 전달"), style = KrdsText.cardTitle)
                        Field(tr("전할 말 (선택)"), fwNote, { fwNote = it }, 300)
                        Row(Modifier.fillMaxWidth().toggleable(fwNotify, role = Role.Checkbox) { fwNotify = it }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(fwNotify, null, colors = CheckboxDefaults.colors(checkedColor = Krds.colors.primary))
                            Text(tr("학생에게도 전달 사실 알리기"), style = KrdsText.body, modifier = Modifier.padding(start = 8.dp))
                        }
                        TeacherSearch(tr("담당 업무·이름"), exclude = me.userId, action = tr("전달")) { t ->
                            run(tr("{name} 선생님께 전달했어요.", "name" to t.name), { app.services.school.forwardVisit(id, t.teacherId, fwNote.trim(), fwNotify) }, after = { app.pop() })
                        }
                    }
                }
            }
        }
    }
}

// ── 소속·명단 (교사) ─────────────────────────────────────────────────────────

@Composable
private fun GroupsTab() {
    val app = LocalApp.current
    Loaded(Unit, { app.services.school.groups() }) { list: GroupList, _ ->
        Scroll {
            PrimaryButton(tr("새 명단"), { app.push(Route.SchoolGroup(0)) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            Text(tr("공용 소속"), style = KrdsText.sectionTitle)
            if (!list.canManageShared) Text(tr("공용 소속은 관리자와 권한 받은 선생님이 고쳐요."), style = KrdsText.meta)
            GroupRows(list.groups.filter { !it.isPrivate })
            Text(tr("내 명단 (나에게만 보여요)"), style = KrdsText.sectionTitle)
            GroupRows(list.groups.filter { it.isPrivate })
        }
    }
}

@Composable
private fun GroupRows(groups: List<SchoolGroup>) {
    val app = LocalApp.current
    if (groups.isEmpty()) Text(tr("없어요."), style = KrdsText.meta)
    groups.forEach { g ->
        KrdsCard(onClick = if (g.canEdit) ({ app.push(Route.SchoolGroup(g.id)) }) else null) {
            Row2 {
                Badge(if (g.isPrivate) tr("내 명단") else tr(g.category), BadgeTone.NEUTRAL)
                Text(g.name, style = KrdsText.cardTitle)
            }
            Text(tr("{n}명", "n" to g.memberCount) + if (g.canEdit) " · " + tr("눌러서 수정") else "", style = KrdsText.meta)
        }
    }
}

@Composable
fun SchoolGroupScreen(id: Int) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    ScreenScaffold(if (id == 0) tr("새 명단") else tr("명단 수정")) {
        Loaded(id, { app.services.school.groups() to (if (id == 0) null else app.services.school.group(id)) }) { (list, g), _ ->
            var kind by remember { mutableStateOf(g?.kind ?: "private") }
            var category by remember { mutableStateOf(g?.category ?: "기타") }
            var name by remember { mutableStateOf(g?.name ?: "") }
            var members by remember { mutableStateOf(g?.membersText ?: "") }
            var confirmDelete by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            Scroll {
                if (id == 0 && list.canManageShared) {
                    Text(tr("종류"), style = KrdsText.cardTitle)
                    Row2 {
                        KrdsChip(tr("나만 보는 명단"), kind == "private") { kind = "private" }
                        KrdsChip(tr("공용 소속"), kind == "shared") { kind = "shared" }
                    }
                }
                if (kind == "shared") KrdsDropdown(tr("분류"), list.categories.map { it to tr(it) }, category, { category = it })
                Field(tr("이름"), name, { name = it }, 60, placeholder = tr("예: 방송부, 2학기 보충반"))
                Field(tr("학번 (한 줄에 한 명, 뒤에 역할을 적을 수 있어요: 20315 부장)"), members, { members = it }, 20000, lines = 8)
                PrimaryButton(tr("저장"), {
                    busy = true
                    scope.launch {
                        runCatching { app.services.school.saveGroup(id, kind, category, name.trim(), members) }
                            .onSuccess { app.toast(tr("저장했어요.")); app.contentChanged(); app.pop() }
                            .onFailure { app.toast(messageOf(it)) }
                        busy = false
                    }
                }, Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank())
                if (id != 0) {
                    if (!confirmDelete) SecondaryButton(tr("삭제"), { confirmDelete = true }, Modifier.fillMaxWidth(), danger = true)
                    else KrdsCard {
                        Text(tr("이 명단을 삭제할까요? 이미 보낸 연락은 그대로 남아요."), style = KrdsText.cardTitle)
                        Row2 {
                            SecondaryButton(tr("삭제"), {
                                scope.launch {
                                    runCatching { app.services.school.deleteGroup(id) }
                                        .onSuccess { app.toast(tr("삭제했어요.")); app.contentChanged(); app.pop() }
                                        .onFailure { app.toast(messageOf(it)) }
                                }
                            }, danger = true)
                            SecondaryButton(tr("취소"), { confirmDelete = false })
                        }
                    }
                }
            }
        }
    }
}

// ── 알림 ────────────────────────────────────────────────────────────────────

@Composable
private fun AlertsTab(me: SchoolMe) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    Loaded(Unit, { app.services.school.notifications() }) { list: List<SchoolNotification>, reload ->
        Scroll {
            SecondaryButton(tr("모두 읽음"), { scope.launch { runCatching { app.services.school.markAllRead() }; reload() } })
            if (list.isEmpty()) Text(tr("아직 알림이 없어요."), style = KrdsText.meta)
            list.forEach { n ->
                KrdsCard(onClick = {
                    // Opening an alert reads it (no need to press 모두 읽음).
                    if (!n.read) scope.launch { runCatching { app.services.school.markRead(n.id) }; reload() }
                    app.openSchoolTarget(n.target, me.role)
                }, modifier = Modifier.semantics { contentDescription = (if (n.read) "" else tr("새 알림") + ", ") + "${n.title}, ${n.body}" }) {
                    Row2 {
                        if (!n.read) Badge(tr("새 알림"), BadgeTone.DANGER)
                        Text(n.title, style = KrdsText.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (n.body.isNotBlank()) Text(n.body, style = KrdsText.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(time(n.createdAt), style = KrdsText.meta)
                }
            }
        }
    }
}
