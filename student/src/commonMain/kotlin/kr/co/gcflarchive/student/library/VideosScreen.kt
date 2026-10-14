package kr.co.gcflarchive.student.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.co.gcflarchive.core.library.VideoEntry
import kr.co.gcflarchive.core.library.normalizedForSearch
import kr.co.gcflarchive.core.util.displayDate
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.SecondaryButton

/** 영상 아카이브 — video.html; videos play in YouTube. */
@Composable
fun VideosScreen() {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<VideoEntry?>(null) }
    val (load, reload) = rememberLoad { app.services.library.videos() }
    val q = query.normalizedForSearch()
    val shown = load.data.orEmpty().filter { q.isEmpty() || it.searchHaystack().contains(q) }

    LibraryFrame(
        title = "영상 아카이브",
        desc = "공연·행사 영상을 유튜브로 볼 수 있어요.",
        searchHint = "영상 제목, 태그 검색",
        load = load,
        onReload = reload,
        onQuery = { query = it },
        total = load.data?.let { "총 ${shown.size}개 영상" },
    ) {
        emptyItem(shown.isEmpty(), query)
        item {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val columns = if (maxWidth >= 600.dp) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    shown.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { v -> VideoCard(v, Modifier.weight(1f), onPlay = { uri.openUri(v.youtubeUrl) }, onInfo = { detail = v }) }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }

    detail?.let { v ->
        DetailSheet("영상 상세 정보", v.title, { detail = null }) {
            DetailPair("분류", v.category, "등록일", displayDate(v.timestamp))
            DetailRow("태그", v.tags.joinToString(", ") { "#$it" })
            DetailInfo("설명", v.notes.ifBlank { "설명이 없어요." })
            SecondaryButton("링크 복사", {
                clipboard.setText(AnnotatedString(v.youtubeUrl))
                app.toast("링크를 복사했어요.")
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.Link)
            PrimaryButton("영상 보기", { uri.openUri(v.youtubeUrl) }, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayCircle)
        }
    }
}

@Composable
private fun VideoCard(video: VideoEntry, modifier: Modifier, onPlay: () -> Unit, onInfo: () -> Unit) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.Black).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
            AsyncImage(model = video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Icon(Icons.Filled.PlayCircle, "재생", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(56.dp))
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(video.title, style = KrdsText.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.padding(top = 4.dp))
                BadgeRow {
                    if (video.category.isNotBlank()) Badge(video.category, BadgeTone.NEUTRAL)
                    video.tags.take(3).forEach { Badge("#$it", BadgeTone.INFO) }
                }
                if (video.notes.isNotBlank()) Text(video.notes, style = KrdsText.meta, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, "상세 정보", tint = Krds.colors.textSecondary) }
        }
    }
}
