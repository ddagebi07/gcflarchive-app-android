package kr.co.gcflarchive.student.more

import kr.co.gcflarchive.student.ui.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.Tab
import kr.co.gcflarchive.student.platform.platformName
import kr.co.gcflarchive.student.settings.APP_VERSION
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PageHeader
import kr.co.gcflarchive.student.ui.contentWidth

private class Entry(val icon: ImageVector, val title: String, val value: String? = null, val open: (() -> Unit)?)

/** 더보기 tab: every menu of the app as one list. */
@Composable
fun MoreScreen() {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    val sections = listOf(
        tr("소통") to listOf(
            Entry(Icons.Filled.Event, tr("학교생활"), tr("시간표 · 연락 · 질문")) { app.push(Route.School()) },
            Entry(Icons.Filled.Forum, tr("커뮤니티")) { app.push(Route.Community) },
            Entry(Icons.Filled.Assignment, tr("설문조사")) { app.push(Route.Surveys) },
            Entry(Icons.Filled.Notifications, tr("공지")) { app.push(Route.Notices) },
        ),
        tr("공부") to listOf(
            Entry(Icons.Filled.Assessment, tr("등급컷 계산기")) { app.push(Route.Grade) },
            Entry(Icons.Filled.Leaderboard, tr("내 점수 기록")) { app.push(Route.MyScores) },
            Entry(Icons.Filled.Quiz, tr("기출문제")) { app.push(Route.PastExams) },
            Entry(Icons.Filled.PictureAsPdf, tr("문서 자료실")) { app.push(Route.Documents) },
        ),
        tr("학교생활") to listOf(
            Entry(Icons.Filled.Restaurant, tr("급식")) { app.tab = Tab.MEAL },
            Entry(Icons.Filled.Map, tr("맛집 지도")) { app.push(Route.Map()) },
            Entry(Icons.Filled.PhotoLibrary, tr("사진")) { app.push(Route.Photos) },
            Entry(Icons.Filled.SmartDisplay, tr("영상")) { app.push(Route.Videos) },
            Entry(Icons.Filled.Search, tr("통합검색")) { app.tab = Tab.SEARCH },
            Entry(Icons.Filled.CloudUpload, tr("극플드라이브")) { app.tab = Tab.DRIVE },
        ),
        tr("앱") to listOf(
            Entry(Icons.Filled.Settings, tr("설정")) { app.push(Route.Settings) },
            Entry(Icons.AutoMirrored.Filled.OpenInNew, tr("웹사이트에서 보기")) { uri.openUri(Config.BASE_URL) },
            Entry(Icons.Filled.Info, "앱 버전", "$APP_VERSION · $platformName", null),
        ),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.contentWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader(tr("더보기"))
            sections.forEach { (title, entries) ->
                Text(title, style = KrdsText.label, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                KrdsCard(padding = PaddingValues(0.dp)) {
                    entries.forEachIndexed { i, e ->
                        if (i > 0) Hairline()
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .let { m -> e.open?.let { m.clickable(onClick = it) } ?: m }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(e.icon, null, tint = Krds.colors.primaryText, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(e.title, style = KrdsText.body, modifier = Modifier.weight(1f))
                            e.value?.let { Text(it, style = KrdsText.meta) }
                            if (e.open != null) Icon(Icons.Filled.ChevronRight, null, tint = Krds.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
