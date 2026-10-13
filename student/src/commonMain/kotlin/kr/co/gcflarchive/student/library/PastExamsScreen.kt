package kr.co.gcflarchive.student.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.library.AnswerSheet
import kr.co.gcflarchive.core.library.ExamFilters
import kr.co.gcflarchive.core.library.LibraryDocument
import kr.co.gcflarchive.core.library.LibraryRepository
import kr.co.gcflarchive.core.library.SubjectConfig
import kr.co.gcflarchive.core.library.parseCutoffs
import kr.co.gcflarchive.core.net.LoginRequiredException
import kr.co.gcflarchive.core.util.formatBytes
import kr.co.gcflarchive.student.AppState
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsChip
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.SecondaryButton
import kr.co.gcflarchive.student.ui.download
import kr.co.gcflarchive.student.ui.userMessage

private class ExamData(val docs: List<LibraryDocument>, val subjects: SubjectConfig, val favorites: Set<String>?)

/** 기출문제 아카이브 — past-exams.html. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PastExamsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(ExamFilters()) }
    var favOverride by remember { mutableStateOf<Set<String>?>(null) }
    var filterSheet by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<LibraryDocument?>(null) }

    val (load, reload) = rememberLoad {
        favOverride = null
        coroutineScope {
            val docs = async { app.services.library.documents().filter { it.isPastExam && !it.isPrivate } }
            val subjects = async { runCatching { app.services.library.subjects() }.getOrDefault(SubjectConfig(emptyList(), ExamFilters.DEFAULT_DEPARTMENTS)) }
            val favs = async { runCatching { app.services.library.favorites() }.getOrNull() }
            ExamData(docs.await(), subjects.await(), favs.await())
        }
    }
    val favorites = favOverride ?: load.data?.favorites

    fun toggleFavorite(doc: LibraryDocument) {
        val current = favorites ?: run { app.toast("즐겨찾기는 로그인 후 이용할 수 있습니다."); app.requireLogin(); return }
        scope.launch {
            try {
                val starred = app.services.library.toggleFavorite(doc.filename)
                favOverride = if (starred) current + doc.filename else current - doc.filename
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginRequiredException) {
                app.requireLogin()
            } catch (e: Exception) {
                app.toast(e.userMessage())
            }
        }
    }

    val shown = load.data?.docs.orEmpty().filter { filters.matches(it, favorites.orEmpty(), query) }.sortedWith(ExamFilters.ORDER)

    LibraryFrame(
        title = "기출문제",
        desc = "지난 시험지를 연도·학년·과목별로 찾아보세요.",
        searchHint = "과목, 연도, 키워드 검색",
        load = load,
        onReload = reload,
        onQuery = { query = it },
        total = load.data?.let { "총 ${shown.size}건" },
        actions = {
            TextButton(onClick = { filterSheet = true }) {
                Icon(Icons.Filled.FilterList, null, tint = Krds.colors.primaryText)
                Text(if (filters.activeCount > 0) " 상세 조건 ${filters.activeCount}" else " 상세 조건", color = Krds.colors.primaryText)
            }
        },
        filters = {
            if (favorites != null || filters.activeCount > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (favorites != null) KrdsChip("★ 즐겨찾기만", filters.favoritesOnly) { filters = filters.copy(favoritesOnly = !filters.favoritesOnly) }
                    filters.years.sortedDescending().forEach { y -> KrdsChip("${y}년 ✕", true) { filters = filters.copy(years = filters.years - y) } }
                    filters.grades.sorted().forEach { g -> KrdsChip("${g}학년 ✕", true) { filters = filters.copy(grades = filters.grades - g) } }
                    filters.departments.forEach { d -> KrdsChip("$d ✕", true) { filters = filters.copy(departments = filters.departments - d) } }
                    filters.periods.forEach { p -> KrdsChip("$p ✕", true) { filters = filters.copy(periods = filters.periods - p) } }
                    filters.subjects.forEach { s -> KrdsChip("$s ✕", true) { filters = filters.copy(subjects = filters.subjects - s) } }
                }
            }
        },
    ) {
        emptyItem(shown.isEmpty(), query)
        items(shown, key = { it.filename }) { doc ->
            ExamRow(doc, favorites?.contains(doc.filename), onStar = { toggleFavorite(doc) }) { detail = doc }
            Hairline(Modifier.padding(horizontal = 16.dp))
        }
    }

    if (filterSheet) {
        load.data?.let { data ->
            FilterSheet(data, filters, favorites.orEmpty(), query, onDismiss = { filterSheet = false }) { filters = it; filterSheet = false }
        }
    }
    detail?.let { doc -> ExamDetail(app, doc) { detail = null } }
}

@Composable
private fun ExamRow(doc: LibraryDocument, starred: Boolean?, onStar: () -> Unit, onClick: () -> Unit) {
    val m = doc.examMeta
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(m.year.takeIf { it > 0 }?.let { "${it}년" }, m.grade.takeIf { it > 0 }?.let { "${it}학년" }).joinToString(" "),
                    style = KrdsText.label.copy(color = Krds.colors.primaryText, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(end = 6.dp),
                )
                BadgeRow {
                    if (m.period.isNotBlank()) Badge(m.period, BadgeTone.NEUTRAL)
                    if (m.department.isNotBlank()) Badge(m.department, BadgeTone.SUCCESS)
                    if (doc.hasAnswerSheet) Badge("답지", BadgeTone.INFO)
                    if (m.cutoffs.isNotBlank() || m.average.isNotBlank()) Badge("등급컷", BadgeTone.PURPLE)
                }
            }
            Text(doc.title, style = KrdsText.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Text(listOf(m.subject.ifBlank { "기출" }, formatBytes(doc.size)).joinToString(" · "), style = KrdsText.meta, modifier = Modifier.padding(top = 2.dp))
        }
        if (starred != null) {
            IconButton(onClick = onStar) {
                Icon(
                    if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                    if (starred) "즐겨찾기 해제" else "즐겨찾기",
                    tint = if (starred) Krds.colors.star else Krds.colors.textSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(data: ExamData, initial: ExamFilters, favorites: Set<String>, query: String, onDismiss: () -> Unit, onApply: (ExamFilters) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    val count = data.docs.count { draft.matches(it, favorites, query) }

    @Composable
    fun <T> Section(title: String, options: List<Pair<String, T>>, selected: Set<T>, onToggle: (T) -> Unit, onTitle: (() -> Unit)? = null) {
        Text(
            title + if (onTitle != null) "  (전체 선택)" else "",
            style = KrdsText.label.copy(color = Krds.colors.textStrong),
            modifier = if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (label, v) -> KrdsChip(label, v in selected) { onToggle(v) } }
        }
    }

    DetailSheet("상세 조건 검색", "원하는 조건을 골라 주세요", onDismiss) {
        val years = data.docs.map { it.examMeta.year }.filter { it > 0 }.distinct().sortedDescending()
        if (years.isNotEmpty()) Section("연도", years.map { "${it}년" to it }, draft.years, { v -> draft = draft.copy(years = draft.years.toggle(v)) })
        Section("학년", ExamFilters.GRADES.map { "${it}학년" to it }, draft.grades, { v -> draft = draft.copy(grades = draft.grades.toggle(v)) })
        Section("학과", data.subjects.departments.map { it to it }, draft.departments, { v -> draft = draft.copy(departments = draft.departments.toggle(v)) })
        Section("시험", ExamFilters.PERIODS.map { it to it }, draft.periods, { v -> draft = draft.copy(periods = draft.periods.toggle(v)) })
        data.subjects.groups.forEachIndexed { i, g ->
            Section(
                if (i == 0) "과목 · ${g.label}" else g.label,
                g.subjects.map { it to it },
                draft.subjects,
                { v -> draft = draft.copy(subjects = draft.subjects.toggle(v)) },
                onTitle = {
                    val allOn = g.subjects.all { it in draft.subjects }
                    draft = draft.copy(subjects = if (allOn) draft.subjects - g.subjects.toSet() else draft.subjects + g.subjects)
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            SecondaryButton("초기화", { draft = ExamFilters(favoritesOnly = draft.favoritesOnly) }, Modifier.weight(1f))
            PrimaryButton("${count}건 보기", { onApply(draft) }, Modifier.weight(2f))
        }
    }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

@Composable
private fun ExamDetail(app: AppState, doc: LibraryDocument, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val m = doc.examMeta
    var withAnswers by remember { mutableStateOf(true) }
    var adding by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<AnswerSheet?>(null) }
    var showCutoffs by remember { mutableStateOf(false) }
    val includeAnswers = !doc.hasAnswerSheet || withAnswers

    DetailSheet("기출문제 상세 정보", doc.title, onDismiss) {
        DetailPair(
            "연도 / 학년",
            listOfNotNull(m.year.takeIf { it > 0 }?.let { "${it}년" }, m.grade.takeIf { it > 0 }?.let { "${it}학년" }).joinToString(" / "),
            "시험", m.period,
        )
        DetailPair("과목 / 학과", listOf(m.subject, m.department).filter { it.isNotBlank() }.joinToString(" / "), "파일 크기", formatBytes(doc.size))
        DetailRow("등급컷", m.cutoffs, Krds.colors.purpleFg)
        DetailRow("평균", m.average.takeIf { it.isNotBlank() }?.let { "${it}점" }, Krds.colors.primaryText)
        DetailInfo("설명", listOf(doc.notes, doc.tags.joinToString(", ") { "#$it" }).filter { it.isNotBlank() }.joinToString("\n"))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (doc.hasAnswerSheet) {
                SecondaryButton("정답지 보기", {
                    scope.launch {
                        try {
                            answer = app.services.library.answerSheet(doc.filename)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: LoginRequiredException) {
                            app.requireLogin()
                        } catch (e: Exception) {
                            app.toast(e.userMessage("정답지를 불러오지 못했습니다."))
                        }
                    }
                }, Modifier.weight(1f), icon = Icons.Filled.Description)
            }
            if (m.cutoffs.isNotBlank() || m.average.isNotBlank()) {
                SecondaryButton("등급컷 표", { showCutoffs = !showCutoffs }, Modifier.weight(1f), icon = Icons.Filled.BarChart)
            }
        }
        if (showCutoffs) CutoffTable(m.cutoffs, m.average)
        answer?.let { a ->
            val body = buildList {
                if (a.answers.isNotBlank()) add(a.answers)
                a.essays.forEachIndexed { i, t -> if (t.isNotBlank()) add("서술형${i + 1}: $t") }
            }.joinToString("\n").ifBlank { "등록된 정답이 없습니다." }
            DetailPair("과목", a.subject, "연도", a.year)
            DetailPair("학기", a.term, "출제 교사", a.teachers.joinToString(", "))
            DetailInfo("정답", body)
            DetailInfo("비고", a.notes)
        }

        if (doc.hasAnswerSheet) {
            Row(Modifier.fillMaxWidth().clickable { withAnswers = !withAnswers }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(withAnswers, { withAnswers = it }, colors = CheckboxDefaults.colors(checkedColor = Krds.colors.primary))
                Text("정답지 포함 (바로보기·다운로드·극플드라이브)", style = KrdsText.body)
            }
        }
        // Stacked full-width so long labels never get cut off.
        SecondaryButton("극플드라이브에 담기", {
            adding = true
            scope.launch {
                try {
                    app.toast(app.services.library.addExamToDrive(doc.filename, includeAnswers))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LoginRequiredException) {
                    app.requireLogin()
                } catch (e: Exception) {
                    app.toast(e.userMessage("극플드라이브에 담지 못했습니다."))
                } finally {
                    adding = false
                }
            }
        }, Modifier.fillMaxWidth(), enabled = !adding, icon = Icons.Filled.CloudUpload)
        SecondaryButton("바로보기", {
            onDismiss()
            app.push(Route.Viewer(doc.filename, doc.title, includeAnswers))
        }, Modifier.fillMaxWidth(), icon = Icons.Filled.Visibility)
        PrimaryButton("다운로드", {
            scope.launch { app.download(LibraryRepository.downloadUrl(doc.filename, includeAnswers), doc.title + ".pdf") }
        }, Modifier.fillMaxWidth(), icon = Icons.Filled.Download)
    }
}

@Composable
private fun CutoffTable(raw: String, average: String) {
    val rows = remember(raw) { parseCutoffs(raw) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("등급", style = KrdsText.label.copy(color = Krds.colors.textStrong), modifier = Modifier.weight(1f))
            Text("점수", style = KrdsText.label.copy(color = Krds.colors.textStrong), modifier = Modifier.weight(1f))
        }
        Hairline()
        if (rows.isEmpty()) Text("등록된 등급컷이 없습니다.", style = KrdsText.meta, modifier = Modifier.padding(vertical = 8.dp))
        rows.forEach { (g, s) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(g, style = KrdsText.body, modifier = Modifier.weight(1f))
                Text(s, style = KrdsText.body, modifier = Modifier.weight(1f))
            }
            Hairline()
        }
        if (average.isNotBlank()) Text("평균 ${average}점", style = KrdsText.body.copy(color = Krds.colors.primaryText), modifier = Modifier.padding(top = 8.dp))
    }
}
