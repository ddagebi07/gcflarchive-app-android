package kr.co.gcflarchive.student.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.Config
import kotlinx.datetime.Clock
import kr.co.gcflarchive.core.community.COMMUNITY_REPORT_REASONS
import kr.co.gcflarchive.core.community.CommunityBoard
import kr.co.gcflarchive.core.community.CommunityComment
import kr.co.gcflarchive.core.community.CommunityException
import kr.co.gcflarchive.core.community.CommunityImage
import kr.co.gcflarchive.core.community.CommunityParser
import kr.co.gcflarchive.core.community.CommunityPost
import kr.co.gcflarchive.core.community.CommunityPostSummary
import kr.co.gcflarchive.core.community.CommunitySort
import kr.co.gcflarchive.core.map.MapLogic
import kr.co.gcflarchive.student.AppState
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.platform.rememberImagePicker
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.InfoBox
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsDropdown
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.Loading
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.SecondaryButton
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.StrongRule

private fun time(iso: String) = CommunityParser.displayTime(iso, Clock.System.now())

/** routes/community.py IMAGES_PER_POST */
private const val MAX_PHOTOS = 10

private fun messageOf(e: Throwable) = e.message ?: "인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요."

// ── 목록 ──────────────────────────────────────────────────────────────────

/** 교내 커뮤니티: 게시판·정렬은 드롭다운, 끝까지 내리면 더 불러와요. */
@Composable
fun CommunityScreen() {
    val app = LocalApp.current
    val repo = app.services.community
    var boards by remember { mutableStateOf<List<CommunityBoard>?>(null) }
    var boardsError by remember { mutableStateOf<Throwable?>(null) }
    var boardId by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(CommunitySort.NEW) }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    val posts = remember { mutableStateListOf<CommunityPostSummary>() }
    var page by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<Throwable?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(app.sessionVersion, reload) {
        boardsError = null
        runCatching { repo.boards() }
            .onSuccess { b ->
                boards = b
                if (boardId == null || b.none { it.id == boardId }) boardId = b.firstOrNull()?.id
            }
            .onFailure { boardsError = it }
    }

    fun loadMore() {
        val id = boardId ?: return
        if (loading || page >= totalPages) return
        loading = true
        listError = null
        scope.launch {
            runCatching { repo.posts(id, page + 1, sort, submitted) }
                .onSuccess { p ->
                    page = p.page
                    totalPages = p.totalPages
                    total = p.totalCount
                    posts.addAll(p.posts)
                }
                .onFailure { listError = it }
            loading = false
        }
    }

    LaunchedEffect(boardId, sort, submitted, app.sessionVersion, app.contentVersion, reload) {
        posts.clear()
        page = 0
        totalPages = 1
        loading = false
        loadMore()
    }
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd) { if (nearEnd && posts.isNotEmpty()) loadMore() }

    val board = boards?.firstOrNull { it.id == boardId }
    ScreenScaffold("커뮤니티") {
        val b = boards
        when {
            boardsError is CommunityException && (boardsError as CommunityException).loginRequired ->
                StateMessage("로그인하면 커뮤니티를 이용할 수 있어요.", null, "로그인하기", { app.requireLogin() }, locked = true)
            boardsError != null -> StateMessage("커뮤니티를 불러오지 못했어요.", messageOf(boardsError!!), "다시 불러오기", { reload++ })
            b == null -> Loading()
            b.isEmpty() -> StateMessage(
                if (app.userId == null) "로그인하면 커뮤니티를 이용할 수 있어요." else "지금은 볼 수 있는 게시판이 없어요.",
                null,
                if (app.userId == null) "로그인하기" else null,
                if (app.userId == null) ({ app.requireLogin() }) else null,
            )
            else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
                    item {
                        Text(board?.description?.ifBlank { null } ?: "학교생활 이야기와 정보를 나눠요.", style = KrdsText.pageDesc)
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KrdsDropdown("게시판", b.map { it.id to it.name }, boardId ?: "", { boardId = it }, Modifier.weight(1f))
                            KrdsDropdown("정렬", CommunitySort.entries.map { it to it.label }, sort, { sort = it }, Modifier.width(120.dp))
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("제목이나 내용으로 찾기") },
                            singleLine = true,
                            trailingIcon = {
                                TextButton(onClick = { submitted = query.trim() }) { Text("찾기") }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text("글 ${total}개", style = KrdsText.meta, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        StrongRule()
                    }
                    items(posts, key = { it.id }) { p ->
                        Column(Modifier.fillMaxWidth().clickable { app.push(Route.CommunityPost(p.id)) }.padding(vertical = 14.dp)) {
                            Text(
                                p.title,
                                style = KrdsText.cardTitle.copy(color = if (p.blinded) Krds.colors.textSecondary else Krds.colors.textStrong),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(p.author.ifBlank { null }, time(p.createdAt).ifBlank { null }, "추천 ${p.likeCount}", "댓글 ${p.commentCount}", if (p.imageCount > 0) "사진 ${p.imageCount}" else null, if (p.hasPoll) "투표" else null).joinToString(" · "),
                                style = KrdsText.meta,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Hairline()
                    }
                    item {
                        when {
                            loading -> Loading()
                            listError != null -> StateMessage("글을 불러오지 못했어요.", messageOf(listError!!), "다시 불러오기", { reload++ })
                            posts.isEmpty() -> StateMessage(if (submitted.isBlank()) "아직 글이 없어요. 첫 글을 남겨보세요." else "찾는 글이 없어요. 다른 낱말로 찾아보세요.")
                        }
                    }
                }
                if (board?.canWrite == true) {
                    ExtendedFloatingActionButton(
                        onClick = { app.push(Route.CommunityWrite(board.id)) },
                        icon = { Icon(Icons.Filled.Edit, null) },
                        text = { Text("글쓰기") },
                        containerColor = Krds.colors.primary,
                        contentColor = Krds.colors.onPrimary,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                    )
                }
            }
        }
    }
}

// ── 글 ────────────────────────────────────────────────────────────────────

/** 글 하나: 본문, 추천(비추천 없음), 댓글, 신고. 내 글·댓글은 수정하거나 삭제할 수 있어요. */
@Composable
fun CommunityPostScreen(id: Int) {
    val app = LocalApp.current
    val repo = app.services.community
    val scope = rememberCoroutineScope()
    var post by remember { mutableStateOf<Result<CommunityPost>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var liked by remember { mutableStateOf(false) }
    var likes by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var confirmDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var editing by remember { mutableStateOf<CommunityComment?>(null) }
    var voting by remember { mutableStateOf(false) }
    var askNickname by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(id, reload, app.sessionVersion, app.contentVersion) {
        val r = runCatching { repo.post(id) }
        r.getOrNull()?.let {
            liked = it.liked
            likes = it.summary.likeCount
        }
        post = r
    }

    fun handle(e: Throwable, retry: (() -> Unit)? = null) {
        when {
            e is CommunityException && e.nicknameRequired -> askNickname = { retry?.invoke() }
            e is CommunityException && e.loginRequired -> app.requireLogin()
            else -> app.toast(messageOf(e))
        }
    }

    fun change(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onSuccess { reload++ }.onFailure { handle(it) } }
    }

    val p = post?.getOrNull()
    ScreenScaffold(
        title = "커뮤니티",
        actions = {
            if (p != null) {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "글 메뉴") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (p.canEdit && !p.summary.blinded) {
                        DropdownMenuItem(text = { Text("수정") }, onClick = {
                            menuOpen = false
                            app.push(Route.CommunityWrite(p.boardId, p.summary.id, p.summary.title, p.content, p.images, p.poll))
                        })
                    }
                    if (p.canEdit) {
                        DropdownMenuItem(text = { Text("삭제") }, onClick = {
                            menuOpen = false
                            confirmDelete = {
                                scope.launch {
                                    runCatching { repo.delete(p.summary.id) }.onSuccess { app.contentChanged(); app.pop() }.onFailure { handle(it) }
                                }
                            }
                        })
                    }
                    if (!p.summary.isMine) {
                        DropdownMenuItem(text = { Text("신고하기") }, onClick = {
                            menuOpen = false
                            reportTarget = "post" to p.summary.id
                        })
                    }
                }
            }
        },
        bottomBar = {
            if (p != null && !p.summary.blinded && p.canComment) {
                Row(
                    Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { if (it.length <= 1000) comment = it },
                        placeholder = { Text("댓글 남기기") },
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = !sending && comment.isNotBlank(),
                        onClick = {
                            fun send() {
                                sending = true
                                scope.launch {
                                    runCatching { repo.comment(id, comment.trim()) }
                                        .onSuccess {
                                            comment = ""
                                            reload++
                                        }
                                        .onFailure { e -> handle(e) { send() } }
                                    sending = false
                                }
                            }
                            send()
                        },
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "댓글 올리기", tint = Krds.colors.primaryText) }
                }
            }
        },
    ) {
        when (val r = post) {
            null -> Loading()
            else -> r.fold(
                onFailure = { e ->
                    if (e is CommunityException && e.loginRequired) StateMessage("로그인하면 볼 수 있어요.", null, "로그인하기", { app.requireLogin() }, locked = true)
                    else StateMessage("글을 불러오지 못했어요.", messageOf(e), "다시 불러오기", { reload++ })
                },
                onSuccess = { d ->
                    val moderator = d.canEdit && !d.summary.isMine
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text(d.summary.title, style = KrdsText.pageTitle)
                        Text(
                            listOfNotNull(d.summary.author.ifBlank { null }, time(d.summary.createdAt), if (d.edited) "수정됨" else null, "조회 ${d.summary.viewCount}").joinToString(" · "),
                            style = KrdsText.meta,
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                        )
                        if (d.summary.blinded && d.canEdit) InfoBox("신고가 쌓여 다른 사람에게는 숨겨진 글이에요. 관리 앱에서 검토해주세요.")
                        Hairline()
                        if (d.content.isNotBlank()) SelectionContainer {
                            Text(d.content, style = KrdsText.body, modifier = Modifier.padding(top = 16.dp))
                        }
                        d.images.forEachIndexed { i, img ->
                            AsyncImage(
                                model = Config.url(img.url),
                                contentDescription = "사진 ${i + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
                                    .aspectRatio(if (img.width > 0 && img.height > 0) img.width.toFloat() / img.height else 4f / 3f)
                                    .clip(RoundedCornerShape(8.dp)).background(Krds.colors.surfaceRaised),
                            )
                        }
                        d.poll?.let { poll ->
                            PollCard(poll, busy = voting, onVote = { choices ->
                                voting = true
                                scope.launch {
                                    runCatching { repo.vote(id, choices) }
                                        .onSuccess { fresh -> if (fresh != null) post = Result.success(d.copy(poll = fresh)) }
                                        .onFailure { handle(it) }
                                    voting = false
                                }
                            })
                        }
                        Box(Modifier.padding(top = 16.dp))
                        SecondaryButton(
                            "추천 $likes",
                            {
                                scope.launch {
                                    runCatching { repo.toggleLike(id) }
                                        .onSuccess { (l, n) -> liked = l; likes = n }
                                        .onFailure { handle(it) }
                                }
                            },
                            Modifier.align(Alignment.CenterHorizontally),
                            enabled = !d.summary.blinded,
                            icon = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        )
                        Text("댓글 ${d.comments.size}개", style = KrdsText.cardTitle, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                        StrongRule()
                        if (d.comments.isEmpty()) Text("아직 댓글이 없어요.", style = KrdsText.meta, modifier = Modifier.padding(vertical = 20.dp))
                        d.comments.forEach { c ->
                            CommentRow(
                                c,
                                moderator = moderator,
                                editing = editing?.id == c.id,
                                onEdit = { editing = c },
                                onCancelEdit = { editing = null },
                                onSave = { text ->
                                    scope.launch {
                                        runCatching { repo.editComment(c.id, text) }
                                            .onSuccess { editing = null; reload++ }
                                            .onFailure { handle(it) }
                                    }
                                },
                                onDelete = { confirmDelete = { change { repo.deleteComment(c.id) } } },
                                onReport = { reportTarget = "comment" to c.id },
                            )
                            Hairline()
                        }
                    }
                },
            )
        }
    }

    reportTarget?.let { (type, targetId) ->
        ReportDialog(onDismiss = { reportTarget = null }) { reason ->
            reportTarget = null
            scope.launch {
                runCatching { repo.report(type, targetId, reason) }
                    .onSuccess { blinded ->
                        app.toast(if (blinded) "신고했어요. 신고가 쌓여 관리자가 확인할 때까지 숨겨져요." else "신고했어요. 관리자가 확인할게요.")
                        if (blinded) reload++
                    }
                    .onFailure { handle(it) }
            }
        }
    }
    confirmDelete?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text("삭제할까요? 삭제한 글과 댓글은 되살릴 수 없어요.") },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("닫기") } },
            confirmButton = { TextButton(onClick = { confirmDelete = null; action() }) { Text("삭제", color = Krds.colors.danger) } },
        )
    }
    askNickname?.let { retry -> NicknameDialog(app, onDismiss = { askNickname = null }) { askNickname = null; retry() } }
}

/** 댓글 한 줄. 수정을 누르면 그 자리에서 입력란으로 바뀌어요 (팝업 없음). */
@Composable
private fun CommentRow(
    c: CommunityComment,
    moderator: Boolean,
    editing: Boolean,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    if (editing) {
        var text by remember(c.id) { mutableStateOf(c.content) }
        var saving by remember(c.id) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(c.author, style = KrdsText.label.copy(color = Krds.colors.textStrong))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 1000) text = it },
                label = { Text("댓글 수정") },
                supportingText = { Text("${text.length}/1,000") },
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SecondaryButton("취소", onCancelEdit)
                PrimaryButton("저장하기", { saving = true; onSave(text.trim()) }, enabled = !saving && text.isNotBlank() && text.trim() != c.content)
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(c.author, style = KrdsText.label.copy(color = Krds.colors.textStrong))
            Text(c.content, style = KrdsText.body.copy(color = if (c.blinded) Krds.colors.textSecondary else Krds.colors.text), modifier = Modifier.padding(top = 4.dp))
            Text(listOfNotNull(time(c.createdAt), if (c.edited) "수정됨" else null).joinToString(" · "), style = KrdsText.meta, modifier = Modifier.padding(top = 4.dp))
        }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "댓글 메뉴", tint = Krds.colors.textSecondary) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (c.isMine && !c.blinded) DropdownMenuItem(text = { Text("수정") }, onClick = { open = false; onEdit() })
                if (c.isMine || moderator) DropdownMenuItem(text = { Text("삭제") }, onClick = { open = false; onDelete() })
                if (!c.isMine) DropdownMenuItem(text = { Text("신고하기") }, onClick = { open = false; onReport() })
            }
        }
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onReport: (String) -> Unit) {
    var picked by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("신고하는 이유를 골라주세요") },
        text = {
            Column {
                COMMUNITY_REPORT_REASONS.forEachIndexed { i, reason ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = i == picked, role = Role.RadioButton) { picked = i },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == picked, onClick = null)
                        Text(reason, style = KrdsText.body, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        confirmButton = { TextButton(onClick = { onReport(COMMUNITY_REPORT_REASONS[picked]) }) { Text("신고하기") } },
    )
}

/** 글쓴이 이름은 지도 후기와 같은 닉네임이에요. 처음 올릴 때 정해요. */
@Composable
private fun NicknameDialog(app: AppState, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("닉네임을 정해주세요") },
        text = {
            Column {
                Text("글과 댓글에는 닉네임이 보여요. 지도 후기와 같은 닉네임을 쓰고, 나중에 더보기 › 설정에서 바꿀 수 있어요. 한글·영문·숫자·밑줄로 2~15자예요.", style = KrdsText.meta)
                OutlinedTextField(name, { name = it; error = null }, singleLine = true, isError = error != null, modifier = Modifier.padding(top = 12.dp))
                error?.let { Text(it, style = KrdsText.meta.copy(color = Krds.colors.danger), modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                error = MapLogic.nicknameError(n)
                if (error == null) scope.launch {
                    runCatching { app.services.map.registerNickname(n) }
                        .onSuccess { app.toast("닉네임을 정했어요."); onDone() }
                        .onFailure { error = messageOf(it) }
                }
            }) { Text("정하기") }
        },
    )
}

// ── 글쓰기 ─────────────────────────────────────────────────────────────────

/** 글쓰기·글 수정. 쓸 수 없는 표현은 서버가 알려 줘요. */
@Composable
fun CommunityWriteScreen(route: Route.CommunityWrite) {
    val app = LocalApp.current
    val repo = app.services.community
    val scope = rememberCoroutineScope()
    val editing = route.editId > 0
    var boards by remember { mutableStateOf<List<CommunityBoard>>(emptyList()) }
    var boardId by remember { mutableStateOf(route.boardId) }
    var title by remember { mutableStateOf(route.title) }
    var content by remember { mutableStateOf(route.content) }
    var anonymous by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askNickname by remember { mutableStateOf<(() -> Unit)?>(null) }
    val images = remember { mutableStateListOf<CommunityImage>().apply { addAll(route.images) } }
    // 투표: an existing poll with votes can't change (the server refuses), so it stays locked.
    val pollLocked = route.poll != null && route.poll.voterCount > 0
    var poll by remember {
        mutableStateOf(route.poll?.let { p -> PollEditorState(p.question, p.options.map { it.text }, p.multiple) })
    }
    var pollRemoved by remember { mutableStateOf(false) }
    var uploading by remember { mutableIntStateOf(0) }
    val pickPhotos = rememberImagePicker(MAX_PHOTOS - images.size) { files ->
        scope.launch {
            for (file in files.take(MAX_PHOTOS - images.size)) {
                uploading++
                runCatching { repo.uploadImage(file) }
                    .onSuccess { images += it; error = null }
                    .onFailure { e -> if (e is CommunityException && e.loginRequired) app.requireLogin() else error = messageOf(e) }
                uploading--
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { repo.me() }.getOrNull()?.let { nickname = it.nickname }
        if (!editing) runCatching { repo.boards() }.getOrNull()?.let { list ->
            boards = list.filter { it.canWrite }
            if (boards.none { it.id == boardId }) boardId = boards.firstOrNull()?.id
        }
    }
    val board = boards.firstOrNull { it.id == boardId }

    fun draft() = poll?.let { p ->
        kr.co.gcflarchive.core.community.PollDraft(p.question, p.options.filter { it.isNotBlank() }, p.multiple, deadlineIso(p.deadlineHours))
    }

    fun submit() {
        if (submitting) return
        error = when {
            title.isBlank() -> "제목을 입력해주세요."
            !pollLocked && poll?.let { p -> kr.co.gcflarchive.core.community.PollDraft(p.question, p.options, p.multiple).error() } != null ->
                poll?.let { p -> kr.co.gcflarchive.core.community.PollDraft(p.question, p.options, p.multiple).error() }
            content.isBlank() && images.isEmpty() && poll == null -> "내용을 입력하거나 사진·투표를 붙여주세요."
            uploading > 0 -> "사진을 올리는 중이에요. 잠시만 기다려주세요."
            !editing && boardId == null -> "게시판을 골라주세요."
            else -> null
        }
        if (error != null) return
        submitting = true
        scope.launch {
            runCatching {
                if (editing) {
                    repo.edit(route.editId, title.trim(), content.trim(), images.map { it.id },
                        poll = if (pollLocked) null else draft(), removePoll = pollRemoved && poll == null)
                    route.editId
                } else {
                    repo.write(boardId!!, title.trim(), content.trim(), anonymous && board?.allowAnonymous == true, images.map { it.id }, draft())
                }
            }.onSuccess { newId ->
                app.contentChanged()
                app.pop()
                if (!editing) app.push(Route.CommunityPost(newId))
                app.toast(if (editing) "글을 수정했어요." else "글을 올렸어요.")
            }.onFailure { e ->
                if (e is CommunityException && e.nicknameRequired) askNickname = { submit() }
                else error = messageOf(e)
            }
            submitting = false
        }
    }

    ScreenScaffold(if (editing) "글 수정" else "글쓰기") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!editing && boards.isNotEmpty()) {
                KrdsDropdown("게시판", boards.map { it.id to it.name }, boardId ?: "", { boardId = it }, Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 100) title = it },
                label = { Text("제목") },
                supportingText = { Text("${title.length}/100") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= 5000) content = it },
                label = { Text("내용") },
                supportingText = { Text("${content.length}/5,000") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("사진 ${images.size}/$MAX_PHOTOS", style = KrdsText.label)
            if (images.isNotEmpty() || uploading > 0) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images, key = { it.id }) { img ->
                        Box(Modifier.size(96.dp)) {
                            AsyncImage(
                                model = Config.url(img.url),
                                contentDescription = "붙인 사진",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Krds.colors.surfaceRaised),
                            )
                            IconButton(
                                onClick = { images.remove(img) },
                                modifier = Modifier.align(Alignment.TopEnd).size(36.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close, "사진 빼기", tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(4.dp),
                                )
                            }
                        }
                    }
                    if (uploading > 0) item {
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).background(Krds.colors.surfaceRaised), contentAlignment = Alignment.Center) {
                            Text("올리는 중", style = KrdsText.meta)
                        }
                    }
                }
            }
            SecondaryButton(
                if (images.size >= MAX_PHOTOS) "사진은 ${MAX_PHOTOS}장까지 붙일 수 있어요" else "사진 붙이기",
                pickPhotos,
                Modifier.fillMaxWidth(),
                enabled = images.size < MAX_PHOTOS && uploading == 0,
                icon = Icons.Filled.AddPhotoAlternate,
            )
            Text("사진에 담긴 위치 정보는 올릴 때 삭제돼요.", style = KrdsText.meta)
            val p = poll
            if (p == null) {
                SecondaryButton("투표 추가", { poll = PollEditorState(); pollRemoved = false }, Modifier.fillMaxWidth(), icon = Icons.Filled.HowToVote)
            } else {
                PollEditor(p, onRemove = { poll = null; pollRemoved = true }, locked = pollLocked)
            }
            if (!editing && board?.allowAnonymous == true) {
                Row(Modifier.clickable { anonymous = !anonymous }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(anonymous, { anonymous = it })
                    Text("익명으로 올리기", style = KrdsText.body)
                }
            }
            if (!editing) {
                Text(nickname?.ifBlank { null }?.let { "$it(으)로 올려요." } ?: "처음 올릴 때 닉네임을 정해요.", style = KrdsText.meta)
            }
            InfoBox("서로 존중해주세요. 욕설·비하·개인정보가 담긴 글은 올라가지 않거나, 신고가 쌓이면 관리자가 확인할 때까지 숨겨져요. 모든 활동은 기록돼요.")
            error?.let { Text(it, style = KrdsText.body.copy(color = Krds.colors.danger)) }
            PrimaryButton(if (editing) "저장하기" else "올리기", { submit() }, Modifier.fillMaxWidth(), enabled = !submitting)
        }
    }
    askNickname?.let { retry -> NicknameDialog(app, onDismiss = { askNickname = null }) { askNickname = null; retry() } }
}
