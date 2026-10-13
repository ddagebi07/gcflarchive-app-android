package kr.co.gcflarchive.student.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.layout.Spacer
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
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.meal.Meal
import kr.co.gcflarchive.core.meal.MealRepository
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PageHeader
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.contentWidth

/**
 * 급식 tab: a swipeable 월~금 strip (one page per week, same weekday kept when
 * swiping), 오늘로, and the selected day's meals from NEIS.
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
        meals = runCatching { app.services.meals.fetch(LocalDate.fromEpochDays(dateDays)) }
        refreshing = false
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { reload++ }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.contentWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeader("급식", Config.SCHOOL_NAME, Modifier.padding(horizontal = 16.dp))

                // Week header: range + 오늘로 (kept in place so the row never jumps).
                val onToday = date == MealWeek.schoolDay(today)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(MealWeek.label(MealWeek.monday(date), today), style = KrdsText.sectionTitle, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { setDate(today) },
                        enabled = !onToday,
                        modifier = Modifier.alpha(if (onToday) 0f else 1f),
                    ) { Text("오늘로") }
                }

                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(76.dp), beyondViewportPageCount = 1) { page ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MealWeek.days(mondayOf(page)).forEachIndexed { i, day ->
                            DayCell(MealWeek.WEEKDAYS[i], day, selected = day == date, isToday = day == today, Modifier.weight(1f)) { setDate(day) }
                        }
                    }
                }
                Hairline()

                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(MealRepository.label(date), style = KrdsText.meta)
                    when (val r = meals) {
                        null -> Unit
                        else -> r.fold(
                            onSuccess = { list ->
                                if (list.isEmpty()) StateMessage("급식이 없는 날입니다.", "공휴일·방학에는 급식이 없어요.")
                                list.forEach { MealCard(it) }
                            },
                            onFailure = { StateMessage("급식 정보를 불러오지 못했습니다.", "네트워크를 확인해 주세요.", "다시 시도", { reload++ }) },
                        )
                    }
                }
            }
        }
    }
}

private const val CENTER = 520
private const val PAGES = CENTER * 2 + 1

@Composable
private fun DayCell(weekday: String, day: LocalDate, selected: Boolean, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val k = Krds.colors
    val onSel = k.onPrimary
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) k.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(weekday, style = KrdsText.label.copy(color = if (selected) onSel else k.textSecondary))
        Text(
            day.dayOfMonth.toString(),
            style = KrdsText.sectionTitle.copy(color = when {
                selected -> onSel
                isToday -> k.primaryText
                else -> k.textStrong
            }),
        )
        Text("오늘", style = KrdsText.label.copy(fontSize = 11.sp, color = if (selected) onSel else k.primaryText), modifier = Modifier.alpha(if (isToday) 1f else 0f))
    }
}

@Composable
private fun MealCard(meal: Meal) {
    KrdsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(meal.kind)
            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.weight(1f))
            meal.calories?.let { Text(it, style = KrdsText.meta) }
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            meal.dishes.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name, style = KrdsText.body, modifier = Modifier.weight(1f))
                    d.allergens?.let { Badge("알레르기 $it", BadgeTone.NEUTRAL) }
                }
            }
        }
        if (meal.dishes.isNotEmpty()) Text("알레르기 번호는 교육청 표기 기준입니다.", style = KrdsText.meta.copy(color = Krds.colors.textDisabled), modifier = Modifier.padding(top = 8.dp))
    }
}
