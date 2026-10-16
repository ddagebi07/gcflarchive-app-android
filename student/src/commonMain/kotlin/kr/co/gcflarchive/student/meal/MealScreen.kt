package kr.co.gcflarchive.student.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kr.co.gcflarchive.core.meal.MealWeek
import kr.co.gcflarchive.student.ui.Hairline
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.meal.Meal
import kr.co.gcflarchive.core.meal.MealRepository
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PageHeader
import kr.co.gcflarchive.student.ui.tr
import kr.co.gcflarchive.student.ui.contentWidth

/**
 * 급식 tab, the same as the Android app's MealFragment:
 * - 주 범위 (누르면 날짜 고르기) + 오늘 보기
 * - ‹ 월~금 ›: 옆으로 밀거나 ‹ ›를 누르면 한 주씩, 같은 요일이 그대로 골라져요
 * - 고른 날: 파란 둥근 사각형, 오늘: 같은 사각형의 테두리
 * - 끼니 카드 (끼니 이름 · 칼로리 · 메뉴(알레르기 번호) · 학교 이름) + 알레르기 번호 안내
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val today = remember { MealRepository.today() }
    val baseMonday = remember { MealWeek.monday(MealWeek.schoolDay(today)) }
    var dateDays by rememberSaveable { mutableIntStateOf(MealWeek.schoolDay(today).toEpochDays()) }
    val date = LocalDate.fromEpochDays(dateDays)
    var meals by remember { mutableStateOf<Result<List<Meal>>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }

    fun pageOf(d: LocalDate) = (CENTER + MealWeek.weekIndex(baseMonday, d)).coerceIn(0, PAGES - 1)
    fun mondayOf(page: Int) = MealWeek.plusWeeks(baseMonday, page - CENTER)
    val pager = rememberPagerState(initialPage = pageOf(date)) { PAGES }

    fun setDate(d: LocalDate) {
        val target = MealWeek.schoolDay(d)
        dateDays = target.toEpochDays()
        val page = pageOf(target)
        scope.launch { if (abs(pager.currentPage - page) <= 3) pager.animateScrollToPage(page) else pager.scrollToPage(page) }
    }

    // Swiping to another week keeps the same weekday selected.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val current = LocalDate.fromEpochDays(dateDays)
            val target = mondayOf(page).plus(DatePeriod(days = current.dayOfWeek.isoDayNumber - 1))
            if (target != current) dateDays = target.toEpochDays()
        }
    }

    LaunchedEffect(dateDays, reload) {
        refreshing = true
        meals = null
        meals = runCatching { app.services.meals.fetch(LocalDate.fromEpochDays(dateDays)) }
        refreshing = false
    }

    val k = Krds.colors
    Column(Modifier.fillMaxSize()) {
        // Android shows '급식' in its top bar; iPhone·iPad have no top bar on tabs, so the title goes here.
        PageHeader("급식", modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        // Week header: range (tap = 날짜 고르기) + 오늘 보기 (kept in place so the row never jumps).
        val onToday = date == MealWeek.schoolDay(today)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = tr("날짜 고르기")) { picking = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(MealWeek.label(MealWeek.monday(date), today), style = KrdsText.sectionTitle.copy(fontSize = 22.sp))
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = k.textSecondary, modifier = Modifier.padding(start = 4.dp).size(20.dp))
            }
            OutlinedButton(onClick = { setDate(today) }, enabled = !onToday, modifier = Modifier.alpha(if (onToday) 0f else 1f)) { Text(tr("오늘 보기")) }
        }

        // ‹ 월~금 › : the arrows move a week, like swiping.
        Row(Modifier.fillMaxWidth().height(80.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, modifier = Modifier.width(36.dp)) {
                Icon(Icons.Filled.ChevronLeft, tr("지난주"), tint = k.textSecondary)
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxHeight(), beyondViewportPageCount = 1) { page ->
                Row(Modifier.fillMaxSize()) {
                    MealWeek.days(mondayOf(page)).forEachIndexed { i, day ->
                        DayCell(MealWeek.WEEKDAYS[i], day, selected = day == date, isToday = day == today, Modifier.weight(1f).padding(horizontal = 3.dp)) { setDate(day) }
                    }
                }
            }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(PAGES - 1)) } }, modifier = Modifier.width(36.dp)) {
                Icon(Icons.Filled.ChevronRight, tr("다음 주"), tint = k.textSecondary)
            }
        }
        Hairline()

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { reload++ }, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.contentWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    // The chosen day, large: the first thing people check on this tab.
                    Text(MealWeek.dayTitle(date, today), style = KrdsText.pageTitle, modifier = Modifier.padding(bottom = 12.dp))
                    when (val r = meals) {
                        null -> MealState("급식을 불러오고 있어요", null)
                        else -> r.fold(
                            onSuccess = { list ->
                                if (list.isEmpty()) MealState("이날은 급식이 없어요.", "주말·공휴일·방학에는 급식이 없어요.")
                                list.forEach { MealCard(it) }
                            },
                            onFailure = { MealState("급식을 불러오지 못했어요.", "인터넷 연결을 확인하고 화면을 아래로 당겨주세요.") },
                        )
                    }
                    Text(tr(ALLERGY_GUIDE), style = KrdsText.meta.copy(lineHeight = 21.sp), modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDays() * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { setDate(LocalDate.fromEpochDays((it / MILLIS_PER_DAY).toInt())) }
                    picking = false
                }) { Text(tr("확인")) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(tr("닫기")) } },
        ) { DatePicker(state) }
    }
}

private const val CENTER = 520
private const val PAGES = CENTER * 2 + 1
private const val MILLIS_PER_DAY = 86_400_000L
private const val ALLERGY_GUIDE = "알레르기 정보: ①난류 ②우유 ③메밀 ④땅콩 ⑤대두 ⑥밀 ⑦고등어 ⑧게 ⑨새우 ⑩돼지고기 ⑪복숭아 ⑫토마토 ⑬아황산류 ⑭호두 ⑮닭고기 ⑯쇠고기 ⑰오징어 ⑱조개류 ⑲잣"

@Composable
private fun MealState(title: String, desc: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr(title), style = KrdsText.sectionTitle, textAlign = TextAlign.Center)
        if (desc != null) Text(tr(desc), style = KrdsText.meta, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Selected: filled rounded square. Today: the same square as an outline, so when today is selected they overlap. */
@Composable
private fun DayCell(weekday: String, day: LocalDate, selected: Boolean, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val k = Krds.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(if (selected) k.primary else Color.Transparent)
            .then(if (isToday && !selected) Modifier.border(2.dp, k.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "${day.monthNumber}월 ${day.dayOfMonth}일 ${weekday}요일" + (if (isToday) ", 오늘" else "") + (if (selected) ", 선택됨" else "")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(weekday, style = KrdsText.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (selected) k.onPrimary else k.textSecondary))
        Text(
            day.dayOfMonth.toString(),
            modifier = Modifier.padding(top = 4.dp),
            style = KrdsText.sectionTitle.copy(color = when {
                selected -> k.onPrimary
                isToday -> k.primaryText
                else -> k.textStrong
            }),
        )
    }
}

/** 끼니 이름(파랑) · 칼로리 배지 / 메뉴 (알레르기 번호) / 학교 이름 — as the Android card. */
@Composable
private fun MealCard(meal: Meal) {
    val k = Krds.colors
    KrdsCard(Modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(meal.kind), style = KrdsText.sectionTitle.copy(color = k.primaryText), modifier = Modifier.weight(1f))
            Text(meal.calories ?: "-", style = KrdsText.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = k.primaryText),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(k.primaryTint).padding(horizontal = 10.dp, vertical = 2.dp))
        }
        Column(Modifier.padding(top = 12.dp)) {
            meal.dishes.forEach { d ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(d.name, style = KrdsText.body, modifier = Modifier.weight(1f))
                    d.allergens?.let { Text("($it)", style = KrdsText.meta, modifier = Modifier.padding(start = 8.dp)) }
                }
            }
        }
        Text(Config.SCHOOL_NAME, style = KrdsText.meta, modifier = Modifier.padding(top = 10.dp))
    }
}
