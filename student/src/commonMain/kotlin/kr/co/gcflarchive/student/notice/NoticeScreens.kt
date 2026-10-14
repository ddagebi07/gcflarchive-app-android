package kr.co.gcflarchive.student.notice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.notice.NoticeLogic
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.library.LibraryFrame
import kr.co.gcflarchive.student.library.emptyItem
import kr.co.gcflarchive.student.library.rememberLoad
import kr.co.gcflarchive.student.platform.shareText
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.HtmlContent
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.Loading
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.StrongRule

/** 공지사항 (notice.html): 중요 공지 first, searchable by title / author. */
@Composable
fun NoticeListScreen() {
    val app = LocalApp.current
    var query by remember { mutableStateOf("") }
    val (load, reload) = rememberLoad { app.services.notices.list() }
    val shown = NoticeLogic.filter(load.data.orEmpty(), query).sortedByDescending { it.critical }

    LibraryFrame(
        title = "공지사항",
        desc = "GCFL 아카이브 운영 소식을 알려드려요.",
        searchHint = "제목, 작성자 검색",
        load = load,
        onReload = reload,
        onQuery = { query = it },
        total = load.data?.let { "총 ${shown.size}건" },
    ) {
        item { StrongRule(Modifier.padding(horizontal = 16.dp)) }
        emptyItem(shown.isEmpty(), query)
        items(shown, key = { it.id }) { n ->
            Column(Modifier.fillMaxWidth().clickable { app.push(Route.NoticeDetail(n.id)) }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (n.critical) {
                        Badge("중요", BadgeTone.DANGER)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(n.title, style = KrdsText.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text("${n.author} · ${n.date} · 조회 ${n.views}", style = KrdsText.meta, modifier = Modifier.padding(top = 4.dp))
            }
            Hairline(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun NoticeDetailScreen(id: Int) {
    val app = LocalApp.current
    val (load, reload) = rememberLoad(id) { app.services.notices.detail(id) }
    val n = load.data
    ScreenScaffold(
        "공지사항",
        actions = {
            if (n != null) IconButton(onClick = { shareText("${n.title}\n${Config.url("/notice-view?id=${n.id}")}") }) { Icon(Icons.Filled.Share, "공유") }
        },
    ) {
        when {
            n != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (n.critical) Badge("중요", BadgeTone.DANGER, Modifier.padding(bottom = 8.dp))
                Text(n.title, style = KrdsText.pageTitle)
                Text("${n.author} · ${n.date} · 조회 ${n.views}", style = KrdsText.meta, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                StrongRule()
                HtmlContent(n.contentHtml, Config.BASE_URL + "/", isSystemInDarkTheme(), Modifier.padding(vertical = 16.dp))
                Hairline()
                Text("목록으로", style = KrdsText.body.copy(color = Krds.colors.primaryText), modifier = Modifier.clickable { app.pop() }.padding(vertical = 16.dp))
            }
            load.error != null -> StateMessage("공지를 불러오지 못했어요.", null, "다시 불러오기", reload)
            else -> Loading()
        }
    }
}
