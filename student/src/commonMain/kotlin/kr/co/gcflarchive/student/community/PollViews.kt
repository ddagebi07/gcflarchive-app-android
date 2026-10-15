package kr.co.gcflarchive.student.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kr.co.gcflarchive.core.community.CommunityParser
import kr.co.gcflarchive.core.community.CommunityPoll
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsDropdown
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.SecondaryButton
import kotlin.time.Duration.Companion.hours

/** Deadline choices for a new poll: label to hours from now (0 = 마감 없음). */
internal val POLL_DEADLINES = listOf("마감 없음" to 0, "1시간 뒤" to 1, "하루 뒤" to 24, "3일 뒤" to 72, "일주일 뒤" to 168)

internal fun deadlineIso(hours: Int): String = if (hours <= 0) "" else (Clock.System.now() + hours.hours).toString()

/** Editable state of the write form's 투표 (2–10 options). */
class PollEditorState(question: String = "", options: List<String> = listOf("", ""), multiple: Boolean = false) {
    var question by mutableStateOf(question)
    var options by mutableStateOf(options)
    var multiple by mutableStateOf(multiple)
    var deadlineHours by mutableStateOf(0)
}

/** 투표 만들기: 질문, 선택지(추가·삭제), 여러 개 고르기, 마감. */
@Composable
fun PollEditor(state: PollEditorState, onRemove: () -> Unit, locked: Boolean) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, Krds.colors.border, RoundedCornerShape(8.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("투표", style = KrdsText.cardTitle, modifier = Modifier.weight(1f))
            if (!locked) SecondaryButton("투표 빼기", onRemove, danger = true)
        }
        if (locked) {
            Text("투표가 시작돼서 질문과 선택지는 수정할 수 없어요.", style = KrdsText.meta)
            return@Column
        }
        OutlinedTextField(
            value = state.question,
            onValueChange = { if (it.length <= 200) state.question = it },
            label = { Text("질문") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.options.forEachIndexed { i, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> if (v.length <= 80) state.options = state.options.toMutableList().also { it[i] = v } },
                    label = { Text("선택지 ${i + 1}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (state.options.size > 2) {
                    IconButton(onClick = { state.options = state.options.toMutableList().also { it.removeAt(i) } }) {
                        Icon(Icons.Filled.Close, "선택지 ${i + 1} 삭제", tint = Krds.colors.textSecondary)
                    }
                }
            }
        }
        if (state.options.size < 10) {
            SecondaryButton("선택지 추가", { state.options = state.options + "" }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(state.multiple, role = Role.Checkbox) { state.multiple = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(state.multiple, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Krds.colors.primary))
            Text("여러 개 고를 수 있게 하기", style = KrdsText.body, modifier = Modifier.padding(start = 12.dp))
        }
        KrdsDropdown("마감", POLL_DEADLINES.map { it.second.toString() to it.first }, state.deadlineHours.toString(),
            { state.deadlineHours = it.toInt() }, Modifier.fillMaxWidth())
    }
}

/** 투표 카드: 고르기 → 투표하기, 결과는 막대로. 다시 투표하거나 취소할 수 있어요. */
@Composable
fun PollCard(poll: CommunityPoll, onVote: (Set<Int>) -> Unit, busy: Boolean) {
    var changing by remember(poll) { mutableStateOf(false) }
    var picked by remember(poll) { mutableStateOf(poll.myChoices) }
    val voted = poll.myChoices.isNotEmpty()
    val choosing = poll.canVote && (!voted || changing)
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp).border(1.dp, Krds.colors.borderStrong, RoundedCornerShape(8.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(poll.question, style = KrdsText.cardTitle)
        Text(
            listOfNotNull(
                if (poll.multiple) "여러 개 고르기" else "하나 고르기",
                "${poll.voterCount}명 참여",
                when {
                    poll.closed -> "마감됨"
                    poll.closesAt.isNotBlank() -> CommunityParser.displayTime(poll.closesAt, Clock.System.now()) + " 마감"
                    else -> null
                },
            ).joinToString(" · "),
            style = KrdsText.meta,
        )
        poll.options.forEachIndexed { i, option ->
            if (choosing) {
                val selected = i in picked
                val toggle = {
                    picked = if (poll.multiple) (if (selected) picked - i else picked + i) else setOf(i)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (selected) Krds.colors.primary else Krds.colors.border, RoundedCornerShape(8.dp))
                        .then(
                            if (poll.multiple) Modifier.toggleable(selected, role = Role.Checkbox) { toggle() }
                            else Modifier.selectable(selected, role = Role.RadioButton) { toggle() },
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (poll.multiple) Checkbox(selected, null, colors = CheckboxDefaults.colors(checkedColor = Krds.colors.primary))
                    else RadioButton(selected, null, colors = RadioButtonDefaults.colors(selectedColor = Krds.colors.primary))
                    Text(option.text, style = KrdsText.body.copy(color = Krds.colors.textStrong), modifier = Modifier.padding(start = 12.dp))
                }
            } else {
                ResultBar(option.text, poll.percent(i), option.votes, mine = i in poll.myChoices, show = poll.showResults)
            }
        }
        when {
            choosing -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (changing) SecondaryButton("취소", { changing = false; picked = poll.myChoices }, Modifier.weight(1f))
                PrimaryButton(if (voted) "다시 투표하기" else "투표하기", { onVote(picked) }, Modifier.weight(1f), enabled = !busy && picked.isNotEmpty())
            }
            poll.canVote && voted -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("투표 취소", { onVote(emptySet()) }, Modifier.weight(1f), enabled = !busy)
                SecondaryButton("다시 고르기", { changing = true }, Modifier.weight(1f), enabled = !busy)
            }
            !poll.showResults -> Text("투표하면 결과를 볼 수 있어요.", style = KrdsText.meta)
        }
    }
}

/** One result row: the bar is a light tint behind dark text, so the label always stays readable. */
@Composable
private fun ResultBar(text: String, percent: Int, votes: Int?, mine: Boolean, show: Boolean) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (mine) Krds.colors.primary else Krds.colors.border, RoundedCornerShape(8.dp)),
    ) {
        if (show) Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().background(Krds.colors.primaryTint))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (mine) Icon(Icons.Filled.CheckCircle, "내가 고른 선택지", tint = Krds.colors.primaryText, modifier = Modifier.padding(end = 8.dp))
            Text(text, style = KrdsText.body.copy(color = Krds.colors.textStrong, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.weight(1f))
            if (show) Text("$percent% · ${votes ?: 0}명", style = KrdsText.label.copy(color = Krds.colors.textStrong))
        }
    }
}
