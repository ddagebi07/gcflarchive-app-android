package kr.co.gcflarchive.student.grade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.grade.GradeAccess
import kr.co.gcflarchive.core.grade.GradeProfile
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.platform.authenticateUser
import kr.co.gcflarchive.student.platform.canAuthenticateUser
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.Loading
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.StrongRule

private sealed interface ScoresState {
    data object Locked : ScoresState
    data object Loading : ScoresState
    data object NoAccess : ScoresState
    data class Ready(val who: String, val profile: GradeProfile) : ScoresState
    data class Error(val message: String) : ScoresState
}

/** 내 점수 기록: 등급컷 계산기에 입력한 점수를 Face ID·Touch ID 확인 후 보여 줘요. */
@Composable
fun MyScoresScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ScoresState>(ScoresState.Locked) }
    var reload by remember { mutableIntStateOf(0) }

    fun unlock() {
        scope.launch {
            if (authenticateUser("내 점수 기록을 봐요")) {
                state = ScoresState.Loading
                reload++
            }
        }
    }
    LaunchedEffect(Unit) { if (canAuthenticateUser()) unlock() }
    LaunchedEffect(reload, app.sessionVersion) {
        if (state is ScoresState.Locked) return@LaunchedEffect
        state = ScoresState.Loading
        state = runCatching {
            when (val access = app.services.grades.access()) {
                is GradeAccess.Verified -> ScoresState.Ready("학번 ${access.userId}로 입력한 점수예요.", app.services.grades.profile(null))
                is GradeAccess.Guest -> ScoresState.Ready("익명(${access.displayName})으로 입력한 점수예요.", app.services.grades.profile(null))
                else -> ScoresState.NoAccess
            }
        }.getOrElse { ScoresState.Error(it.message ?: "인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요.") }
    }

    ScreenScaffold("내 점수 기록") {
        when (val s = state) {
            ScoresState.Locked ->
                if (!canAuthenticateUser()) StateMessage("기기에 Face ID·Touch ID나 암호를 설정하면 점수를 볼 수 있어요.", locked = true)
                else StateMessage("본인인지 확인하면 점수를 볼 수 있어요.", null, "확인하고 보기", { unlock() }, locked = true)
            ScoresState.Loading -> Loading()
            ScoresState.NoAccess -> StateMessage("등급컷 계산기에서 로그인하거나 익명으로 시작하면 기록이 쌓여요.", null, "등급컷 계산기 열기", { app.push(Route.Grade) })
            is ScoresState.Error -> StateMessage("점수를 불러오지 못했어요.", s.message, "다시 불러오기", { reload++ })
            is ScoresState.Ready -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.who, style = KrdsText.pageDesc)
                StrongRule()
                if (s.profile.scores.isEmpty()) StateMessage("아직 입력한 점수가 없어요.", null, "등급컷 계산기 열기", { app.push(Route.Grade) })
                s.profile.scores.forEach { MyScoreCard(it) }
            }
        }
    }
}
