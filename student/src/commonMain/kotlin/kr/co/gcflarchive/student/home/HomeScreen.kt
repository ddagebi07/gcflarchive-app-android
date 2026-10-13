package kr.co.gcflarchive.student.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.auth.LoginState
import kr.co.gcflarchive.core.meal.Meal
import kr.co.gcflarchive.core.meal.MealRepository
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.Tab
import kr.co.gcflarchive.student.platform.rememberFilePicker
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.contentWidth

private data class Tile(val label: String, val icon: ImageVector, val open: () -> Unit)

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    var meal by remember { mutableStateOf<Result<Meal?>?>(null) }
    LaunchedEffect(Unit) {
        meal = runCatching { MealRepository.featured(app.services.meals.fetch(MealRepository.today()), MealRepository.nowHour()) }
    }
    val pick = rememberFilePicker { files -> app.push(Route.Upload(files)) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.contentWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = app.login) {
                is LoginState.LoggedIn -> Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${s.userId}님") }
                        append(", 환영합니다!")
                    },
                    style = KrdsText.sectionTitle.copy(fontWeight = FontWeight.Normal),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                LoginState.LoggedOut -> LoginBanner { app.requireLogin() }
                LoginState.Unknown -> Unit
            }

            // 오늘의 급식
            KrdsCard(onClick = { app.tab = Tab.MEAL }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Restaurant, null, tint = Krds.colors.primaryText, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("오늘의 급식", style = KrdsText.cardTitle, modifier = Modifier.weight(1f))
                    meal?.getOrNull()?.let { Badge(it.kind) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when (val r = meal) {
                        null -> "불러오는 중…"
                        else -> r.fold(
                            onSuccess = { m -> m?.dishes?.joinToString(" · ") { it.name } ?: "오늘은 급식이 없습니다." },
                            onFailure = { "급식 정보를 불러오지 못했습니다." },
                        )
                    },
                    style = KrdsText.body,
                )
            }

            // 극플드라이브 업로드
            Column(
                Modifier.fillMaxWidth().background(Krds.colors.primary, RoundedCornerShape(12.dp)).clickable(onClick = pick).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudUpload, null, tint = Krds.colors.onPrimary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("극플드라이브에 파일 올리기", style = KrdsText.cardTitle.copy(color = Krds.colors.onPrimary))
                        Text("PDF·한글·오피스 문서를 올리고 학교에서 바로 인쇄하세요.", style = KrdsText.meta.copy(color = Krds.colors.onPrimary.copy(alpha = 0.85f)))
                    }
                }
            }

            Text("아카이브", style = KrdsText.sectionTitle, modifier = Modifier.padding(top = 8.dp))
            val tiles = listOf(
                Tile("기출", Icons.Filled.Description) { app.push(Route.PastExams) },
                Tile("PDF", Icons.Filled.PictureAsPdf) { app.push(Route.Documents) },
                Tile("사진", Icons.Filled.Photo) { app.push(Route.Photos) },
                Tile("영상", Icons.Filled.PlayCircle) { app.push(Route.Videos) },
                Tile("통합 검색", Icons.Filled.Search) { app.tab = Tab.SEARCH },
                Tile("등급컷", Icons.Filled.BarChart) { app.push(Route.Grade) },
                Tile("맛집 지도", Icons.Filled.Place) { app.push(Route.Map()) },
                Tile("공지", Icons.Filled.Campaign) { app.push(Route.Notices) },
            )
            // Rows of four equal tiles with fixed gaps.
            tiles.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t -> ArchiveTile(t, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            TextButton(onClick = { uri.openUri(Config.BASE_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text("브라우저에서 웹사이트 열기", color = Krds.colors.primaryText)
            }
        }
    }
}

@Composable
private fun ArchiveTile(t: Tile, modifier: Modifier) {
    Column(
        modifier
            .heightIn(min = 84.dp)
            .background(Krds.colors.surfaceRaised, RoundedCornerShape(16.dp))
            .clickable(onClick = t.open)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(t.icon, null, tint = Krds.colors.primaryText, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(t.label, style = KrdsText.label.copy(color = Krds.colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LoginBanner(onLogin: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Krds.colors.primary, RoundedCornerShape(16.dp)).padding(20.dp)) {
        Icon(Icons.Filled.Lock, null, tint = Krds.colors.onPrimary, modifier = Modifier.size(36.dp))
        Text("로그인하고 GCFL 아카이브를 모두 이용하세요", style = KrdsText.sectionTitle.copy(color = Krds.colors.onPrimary), modifier = Modifier.padding(top = 12.dp))
        Text(
            "기출 정답지·즐겨찾기, 사진 아카이브, 극플드라이브 업로드는 재학생 인증 후 이용할 수 있어요.",
            style = KrdsText.meta.copy(color = Krds.colors.onPrimary.copy(alpha = 0.85f)),
            modifier = Modifier.padding(top = 6.dp),
        )
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Krds.colors.surface, contentColor = Krds.colors.primaryText),
        ) { Text("학번으로 로그인", fontWeight = FontWeight.Bold) }
    }
}
