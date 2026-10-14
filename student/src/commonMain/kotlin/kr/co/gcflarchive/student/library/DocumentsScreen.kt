package kr.co.gcflarchive.student.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.library.DocCategory
import kr.co.gcflarchive.core.library.DocSort
import kr.co.gcflarchive.core.library.LibraryDocument
import kr.co.gcflarchive.core.library.LibraryRepository
import kr.co.gcflarchive.core.library.normalizedForSearch
import kr.co.gcflarchive.core.util.displayDate
import kr.co.gcflarchive.core.util.formatBytes
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.ChoiceChips
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.SecondaryButton
import kr.co.gcflarchive.student.ui.download

/** 문서 자료실 (PDF 아카이브) — documents.html. */
@Composable
fun DocumentsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DocCategory.ALL) }
    var sort by remember { mutableStateOf(DocSort.DATE_DESC) }
    var sortMenu by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<LibraryDocument?>(null) }
    val (load, reload) = rememberLoad { app.services.library.documents().filter { !it.isPastExam && !it.isPrivate } }

    val all = load.data.orEmpty()
    val q = query.normalizedForSearch()
    val shown = all.filter { category.matches(it.category) }.filter { q.isEmpty() || it.searchHaystack().contains(q) }.sortedWith(sort.comparator)

    LibraryFrame(
        title = "PDF 자료실",
        desc = "수업자료·행정서식·학교 규정을 모아 두었어요.",
        searchHint = "자료 이름, 태그 검색",
        load = load,
        onReload = reload,
        onQuery = { query = it },
        total = load.data?.let { "총 ${shown.size}건" },
        actions = {
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, null, tint = Krds.colors.primaryText)
                    Text(" ${sort.label}", color = Krds.colors.primaryText)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    DocSort.entries.forEach { s ->
                        DropdownMenuItem(text = { Text((if (s == sort) "✓ " else "") + s.label) }, onClick = { sort = s; sortMenu = false })
                    }
                }
            }
        },
        filters = {
            ChoiceChips(DocCategory.entries.map { c -> c to "${c.label} (${all.count { c.matches(it.category) }})" }, category, { category = it })
        },
    ) {
        emptyItem(shown.isEmpty(), query)
        items(shown, key = { it.filename }) { doc ->
            Row(Modifier.fillMaxWidth().clickable { detail = doc }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (doc.isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.InsertDriveFile, null,
                    tint = if (doc.isPdf) Krds.colors.danger else Krds.colors.primaryText, modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BadgeRow {
                        Badge(doc.category.ifBlank { "기타" }, BadgeTone.NEUTRAL)
                        if (doc.collectionName.isNotBlank()) Badge(doc.collectionName, BadgeTone.SUCCESS)
                    }
                    Text(doc.title, style = KrdsText.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(displayDate(doc.modified), formatBytes(doc.size), "조회 ${doc.viewCount}").joinToString(" · "), style = KrdsText.meta)
                }
            }
            Hairline(Modifier.padding(horizontal = 16.dp))
        }
    }

    detail?.let { doc ->
        DetailSheet("자료 상세 정보", doc.title, { detail = null }) {
            DetailPair("분류", doc.category.ifBlank { "기타" }, "컬렉션", doc.collectionName)
            DetailPair("등록일", displayDate(doc.modified), "파일 크기", formatBytes(doc.size))
            DetailRow("이용 현황", "조회 ${doc.viewCount}회 · 다운로드 ${doc.downloadCount}회")
            DetailInfo("설명", listOf(doc.notes, doc.tags.joinToString(", ") { "#$it" }).filter { it.isNotBlank() }.joinToString("\n"))
            if (doc.isPdf) {
                SecondaryButton("바로보기", {
                    detail = null
                    app.push(Route.Viewer(doc.filename, doc.title, true))
                }, Modifier.fillMaxWidth(), icon = Icons.Filled.Visibility)
            }
            PrimaryButton("다운로드", {
                scope.launch { app.download(LibraryRepository.downloadUrl(doc.filename), doc.displayName.ifBlank { doc.filename }) }
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.Download)
        }
    }
}
