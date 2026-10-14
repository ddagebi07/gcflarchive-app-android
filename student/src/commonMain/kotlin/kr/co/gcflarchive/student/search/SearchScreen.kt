package kr.co.gcflarchive.student.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.archive.ArchiveRepository
import kr.co.gcflarchive.core.archive.Board
import kr.co.gcflarchive.core.archive.PostSummary
import kr.co.gcflarchive.core.archive.SearchQuery
import kr.co.gcflarchive.core.archive.SearchSort
import kr.co.gcflarchive.core.util.displayDate
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.KrdsDropdown
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsChip
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.Loading
import kr.co.gcflarchive.student.ui.PageHeader
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.StrongRule
import kr.co.gcflarchive.student.ui.contentWidth

/** 통합검색: posts and attachments crawled from the school homepage (/api/archive/search). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var query by remember { mutableStateOf(SearchQuery()) }
    var boards by remember { mutableStateOf<List<Board>>(emptyList()) }
    val items = remember { mutableStateListOf<PostSummary>() }
    var page by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var totalCount by remember { mutableIntStateOf(-1) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val list = rememberLazyListState()

    fun load(append: Boolean) {
        job?.cancel()
        val q = query
        val target = if (append) page + 1 else 1
        if (append) loadingMore = true else { refreshing = true; failed = false }
        job = scope.launch {
            val r = runCatching { app.services.archive.search(q, target) }
            refreshing = false
            loadingMore = false
            r.onSuccess { res ->
                if (!append) items.clear()
                items += res.items
                page = res.page
                totalPages = res.totalPages
                totalCount = res.totalCount
                if (!append) list.scrollToItem(0)
            }.onFailure { if (!append) { items.clear(); failed = true; totalCount = -1 } }
        }
    }

    LaunchedEffect(Unit) {
        boards = runCatching { app.services.archive.boards() }.getOrDefault(emptyList())
    }
    LaunchedEffect(query) { load(append = false) }

    // Infinite scroll: next page a few rows before the end.
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(list) {
        snapshotFlow { nearEnd }.collect { if (it && !refreshing && !loadingMore && page in 1 until totalPages) load(append = true) }
    }

    fun submit() {
        focus.clearFocus()
        query = query.copy(q = input.trim())
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { load(append = false) }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.contentWidth().fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PageHeader("통합검색", "학교 홈페이지 게시물과 첨부파일을 한 번에 찾아보세요.")
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("검색어 (예: 가정통신문, 수행평가)") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = {
                                if (input.isNotEmpty()) IconButton(onClick = { input = ""; submit() }) { Icon(Icons.Filled.Clear, "지우기") }
                            },
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submit() }),
                        )
                        // 게시판·정렬은 드롭다운, 첨부파일 조건은 칩 하나: 버튼이 줄줄이 늘어서지 않게.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KrdsDropdown(
                                "게시판",
                                listOf<Pair<String?, String>>(null to "전체") + boards.map { it.code to it.name },
                                query.board,
                                { query = query.copy(board = it) },
                                Modifier.weight(1f),
                            )
                            KrdsDropdown("정렬", SearchSort.entries.map { it to it.label }, query.sort, { query = query.copy(sort = it) }, Modifier.width(120.dp))
                        }
                        KrdsChip("첨부파일 있는 글만", query.attachmentsOnly) { query = query.copy(attachmentsOnly = !query.attachmentsOnly) }
                        if (totalCount >= 0) {
                            Text(
                                buildAnnotatedString {
                                    append(if (query.q.isBlank()) "최근 게시물 " else "검색 결과 ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Krds.colors.primaryText)) { append(totalCount.toString()) }
                                    append("건")
                                },
                                style = KrdsText.meta,
                            )
                        }
                    }
                    StrongRule(Modifier.padding(horizontal = 16.dp))
                }
                if (failed) item {
                    StateMessage("검색하지 못했어요.", "인터넷 연결을 확인하고 다시 시도해주세요.", "다시 불러오기", { load(append = false) })
                } else if (totalCount == 0) item {
                    StateMessage(if (query.q.isBlank()) "조건에 맞는 글이 없어요. 조건을 바꿔보세요." else "‘${query.q}’(으)로 찾은 글이 없어요.", "다른 검색어나 게시판을 선택해 보세요.")
                }
                itemsIndexed(items, key = { i, p -> "${p.boardCode}/${p.idx}/$i" }) { i, post ->
                    if (i > 0) Hairline(Modifier.padding(horizontal = 16.dp))
                    SearchRow(post) { app.push(Route.PostDetail(post.boardCode, post.idx)) }
                }
                if (loadingMore) item { Loading() }
            }
        }
    }
}

@Composable
private fun SearchRow(post: PostSummary, onClick: () -> Unit) {
    val highlight = Krds.colors.highlight
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(post.boardName.ifBlank { post.boardCode }, BadgeTone.INFO)
            Spacer(Modifier.width(8.dp))
            Text(displayDate(post.postedAt), style = KrdsText.meta)
            Spacer(Modifier.weight(1f))
            if (post.attachmentCount > 0) {
                Icon(Icons.Filled.AttachFile, null, tint = Krds.colors.textSecondary, modifier = Modifier.padding(end = 2.dp).width(14.dp))
                Text("${post.attachmentCount}", style = KrdsText.meta)
            }
        }
        Text(post.title, style = KrdsText.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        val runs = remember(post.snippet) { ArchiveRepository.snippetRuns(post.snippet) }
        if (runs.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    runs.forEach { (text, marked) ->
                        if (marked) withStyle(SpanStyle(background = highlight, fontWeight = FontWeight.Bold)) { append(text) } else append(text)
                    }
                },
                style = KrdsText.meta.copy(color = Krds.colors.text),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(post.author, style = KrdsText.meta, modifier = Modifier.padding(top = 4.dp))
    }
}
