package kr.co.gcflarchive.student.meal

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
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

/** 급식 tab: one day at a time from NEIS, ← / → and 오늘. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealScreen() {
    val app = LocalApp.current
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val date = MealRepository.shift(MealRepository.today(), offset)
    var meals by remember { mutableStateOf<Result<List<Meal>>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(date, reload) {
        refreshing = true
        meals = runCatching { app.services.meals.fetch(date) }
        refreshing = false
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { reload++ }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.contentWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeader("급식", Config.SCHOOL_NAME)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { offset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 날") }
                    Text(MealRepository.label(date), style = KrdsText.sectionTitle, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { offset++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 날") }
                }
                if (offset != 0) TextButton(onClick = { offset = 0 }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("오늘로 이동") }
                when (val r = meals) {
                    null -> Unit
                    else -> r.fold(
                        onSuccess = { list ->
                            if (list.isEmpty()) StateMessage("급식이 없는 날입니다.", "주말·공휴일·방학에는 급식이 없어요.")
                            list.forEach { MealCard(it) }
                        },
                        onFailure = { StateMessage("급식 정보를 불러오지 못했습니다.", "네트워크를 확인해 주세요.", "다시 시도", { reload++ }) },
                    )
                }
            }
        }
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
