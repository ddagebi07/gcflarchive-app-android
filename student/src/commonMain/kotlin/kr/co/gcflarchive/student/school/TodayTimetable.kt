package kr.co.gcflarchive.student.school

import kr.co.gcflarchive.student.ui.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kr.co.gcflarchive.core.Services
import kr.co.gcflarchive.core.school.TtWeek
import kr.co.gcflarchive.core.school.teacherLabel
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsText

/**
 * 홈의 '내 시간표' 카드: 오늘(주말이면 다음 월요일) 1~7교시를 한 줄로, 지금 교시는 강조해요.
 * Nothing is drawn when there is no timetable for this account (not logged in, no 시간표 yet, …).
 * [refreshKey]: change it to reload (e.g. when the home screen comes back).
 */
@Composable
fun TodayTimetableCard(
    services: Services, refreshKey: Any?, modifier: Modifier = Modifier,
    /** Card background; the Android home passes its own card color so it matches the cards around it. */
    containerColor: Color? = null,
    onOpen: () -> Unit,
) {
    var week by remember { mutableStateOf<TtWeek?>(null) }
    LaunchedEffect(refreshKey) {
        runCatching { services.school.myWeek(null) }.onSuccess { week = it }.onFailure { week = null }
    }
    val w = week?.takeIf { it.hasTimetable && it.days.isNotEmpty() } ?: return
    val now = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Seoul"))
    val hm = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
    TodayTimetableContent(w, hm, TimetableNames.full(services.prefs), modifier, containerColor, onOpen)
}

/** The card itself for a loaded [w]; [hm] is the current time (HH:MM) for the 지금 교시 highlight. */
@Composable
internal fun TodayTimetableContent(w: TtWeek, hm: String, fullNames: Boolean, modifier: Modifier = Modifier, containerColor: Color? = null, onOpen: () -> Unit = {}) {
    val day = w.days.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: 0
    val isToday = w.days[day].isToday
    val title = if (isToday) tr("오늘 시간표") else tr("{day}요일 시간표", "day" to w.days[day].weekday)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(containerColor ?: Krds.colors.surface)
            .clickable(onClickLabel = tr("학교생활 시간표 열기"), onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, null, tint = Krds.colors.primaryText, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = KrdsText.cardTitle, modifier = Modifier.weight(1f))
            Text(w.days[day].date.takeLast(5).replace('-', '/').trimStart('0'), style = KrdsText.meta)
            Icon(Icons.Filled.ChevronRight, null, tint = Krds.colors.textSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            w.classPeriods.forEach { p ->
                val c = w.cell(day, p)
                val current = isToday && p.start <= hm && hm < p.end
                val subject = if (c.empty) "" else c.shortOrFull(fullNames)
                // 교사 시간표(칸에 반이 있음): 반(2-3)을 크게, 과목은 작게. 학생: 과목을 크게, 선생님은 작게.
                val teacherView = c.place.isNotBlank()
                val name = if (teacherView) c.place else subject
                val small = when {
                    c.empty -> ""
                    teacherView -> subject
                    else -> c.teachers.firstOrNull()?.let(::teacherLabel).orEmpty()
                }
                Column(
                    Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (current) Krds.colors.primaryTint else Krds.colors.surfaceRaised)
                        .then(if (current) Modifier.border(2.dp, Krds.colors.primary, RoundedCornerShape(12.dp)) else Modifier)
                        .then(if (c.changed && !current) Modifier.border(1.dp, Krds.colors.danger, RoundedCornerShape(12.dp)) else Modifier)
                        .semantics { contentDescription = "${p.label} " + (if (c.empty) tr("수업 없음") else c.title) + (if (current) ", " + tr("지금") else "") }
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(p.key, style = KrdsText.label.copy(fontSize = 11.sp, color = if (current) Krds.colors.primaryText else Krds.colors.textSecondary))
                    Text(name.ifEmpty { "—" }, maxLines = if (teacherView || name == c.subject) 1 else 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        style = KrdsText.label.copy(fontSize = if (name.length <= 4) 14.sp else 11.sp, lineHeight = 13.sp,
                            fontWeight = FontWeight.Bold, color = if (c.empty) Krds.colors.textSecondary else Krds.colors.textStrong))
                    if (small.isNotBlank()) Text(small, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        style = KrdsText.label.copy(fontSize = 10.sp))
                }
            }
        }
    }
}
