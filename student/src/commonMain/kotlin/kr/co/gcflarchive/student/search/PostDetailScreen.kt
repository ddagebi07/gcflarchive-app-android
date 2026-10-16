package kr.co.gcflarchive.student.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kr.co.gcflarchive.student.ui.appInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.archive.PostDetail
import kr.co.gcflarchive.core.archive.PostRef
import kr.co.gcflarchive.core.archive.PreviewKinds
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.core.util.displayDate
import kr.co.gcflarchive.core.util.formatBytes
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.platform.shareText
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.HtmlContent
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.Loading
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.SecondaryButton
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.StrongRule
import kr.co.gcflarchive.student.ui.download

/** One school-homepage post with attachments and 이전/다음 글. */
@Composable
fun PostDetailScreen(board: String, idx: String) {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(idx) }
    var post by remember { mutableStateOf<Result<PostDetail>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()

    LaunchedEffect(current, reload) {
        post = null
        post = runCatching { app.services.archive.detail(board, current) }
        scroll.scrollTo(0)
    }
    val p = post?.getOrNull()

    ScreenScaffold(
        title = p?.boardName ?: "글",
        actions = {
            if (p != null) {
                // The archive's own link, so the post opens here instead of on the school site.
                IconButton(onClick = { shareText(listOf(p.title, p.shareUrl).joinToString("\n")) }) { Icon(Icons.Filled.Share, "공유하기") }
                p.originalUrl?.let { u -> IconButton(onClick = { uri.openUri(u) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "학교 홈페이지에서 보기") } }
            }
        },
    ) {
        when (val r = post) {
            null -> Loading()
            else -> r.fold(
                onFailure = { StateMessage("글을 불러오지 못했어요.", "인터넷 연결을 확인하고 다시 불러와주세요.", "다시 불러오기", { reload++ }) },
                onSuccess = { d ->
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
                        Badge(d.boardName)
                        Text(d.title, style = KrdsText.pageTitle, modifier = Modifier.padding(top = 10.dp))
                        Text("${d.author} · ${displayDate(d.postedAt)}", style = KrdsText.meta, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                        StrongRule()
                        HtmlContent(d.contentHtml, d.originalUrl ?: "https://www.gcfl.or.kr/", appInDarkTheme(), Modifier.padding(vertical = 16.dp))

                        if (d.attachments.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().background(Krds.colors.surfaceRaised, RoundedCornerShape(8.dp)).padding(vertical = 6.dp)) {
                                Text("첨부파일 ${d.attachments.size}개", style = KrdsText.label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                                d.attachments.forEach { a ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { scope.launch { app.download(a.downloadUrl, a.filename) } }.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Filled.Download, null, tint = Krds.colors.primaryText, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(a.filename, style = KrdsText.body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (a.fileSize > 0) Text(formatBytes(a.fileSize), style = KrdsText.meta, modifier = Modifier.padding(start = 8.dp))
                                        if (a.canPreview) {
                                            TextButton(onClick = {
                                                when (a.previewKind) {
                                                    // HWP is drawn by the site's own viewer page.
                                                    PreviewKinds.HWP -> a.previewPage?.let { uri.openUri(Config.url(it)) }
                                                    else -> app.push(Route.Preview(a.previewApi!!, a.previewKind, a.filename, a.downloadUrl))
                                                }
                                            }) { Text("미리보기") }
                                        }
                                    }
                                }
                            }
                        }

                        if (d.prev != null || d.next != null) {
                            Column(Modifier.padding(top = 20.dp)) {
                                StrongRule()
                                NavRow("이전 글", d.prev, "이전 글이 없어요.") { current = it }
                                Hairline()
                                NavRow("다음 글", d.next, "다음 글이 없어요.") { current = it }
                                Hairline()
                            }
                        }
                        d.originalUrl?.let { u ->
                            SecondaryButton("학교 홈페이지에서 보기", { uri.openUri(u) }, Modifier.fillMaxWidth().padding(top = 20.dp), icon = Icons.AutoMirrored.Filled.OpenInNew)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun NavRow(label: String, ref: PostRef?, empty: String, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().let { if (ref != null) it.clickable { onOpen(ref.idx) } else it }.padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = KrdsText.label.copy(color = Krds.colors.textStrong), modifier = Modifier.width(52.dp))
        Text(ref?.title ?: empty, style = KrdsText.body.copy(color = if (ref != null) Krds.colors.text else Krds.colors.textDisabled), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
