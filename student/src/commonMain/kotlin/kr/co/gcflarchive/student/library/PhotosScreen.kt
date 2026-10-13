package kr.co.gcflarchive.student.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.co.gcflarchive.core.library.PhotoAlbum
import kr.co.gcflarchive.core.library.normalizedForSearch
import kr.co.gcflarchive.core.util.displayDate
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.SecondaryButton

/** 사진 아카이브 — photo.html. Albums open in the browser / Google Photos. */
@Composable
fun PhotosScreen() {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<PhotoAlbum?>(null) }
    val (load, reload) = rememberLoad { app.services.library.photoAlbums() }
    val q = query.normalizedForSearch()
    val shown = load.data.orEmpty().filter { q.isEmpty() || it.searchHaystack().contains(q) }

    LibraryFrame(
        title = "사진 아카이브",
        desc = "행사·공연 사진 앨범을 모았습니다.",
        searchHint = "앨범 이름, 출연진 검색",
        load = load,
        onReload = reload,
        onQuery = { query = it },
        total = load.data?.let { "총 ${shown.size}개 앨범" },
    ) {
        emptyItem(shown.isEmpty(), query)
        item {
            // 2 columns on phones, 3 on iPad / tablets.
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val columns = if (maxWidth >= 600.dp) 3 else 2
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    shown.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { album -> AlbumCard(album, Modifier.weight(1f)) { detail = album } }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }

    detail?.let { a ->
        DetailSheet("앨범 상세 정보", a.title, { detail = null }) {
            DetailPair("날짜", displayDate(a.albumDate), "저작권", a.copyright.ifBlank { "GCFL Archive" })
            DetailRow("컬렉션", a.collectionName.takeIf { it != a.title })
            DetailRow("출연", a.performers.joinToString(", "))
            DetailInfo("설명", a.notes)
            SecondaryButton("링크 복사", {
                clipboard.setText(AnnotatedString(a.albumUrl))
                app.toast("링크를 복사했습니다.")
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.Link)
            PrimaryButton("앨범 열기", { if (a.albumUrl.isNotBlank()) uri.openUri(a.albumUrl) }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.OpenInNew)
        }
    }
}

@Composable
private fun AlbumCard(album: PhotoAlbum, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = album.thumbnailUrl.ifBlank { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(10.dp)).background(Krds.colors.surfaceRaised),
        )
        Text(album.title, style = KrdsText.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(displayDate(album.albumDate), style = KrdsText.meta)
        if (album.performers.isNotEmpty()) {
            Spacer(Modifier.padding(top = 4.dp))
            BadgeRow { album.performers.take(3).forEach { Badge(it, BadgeTone.NEUTRAL) } }
        }
    }
}
