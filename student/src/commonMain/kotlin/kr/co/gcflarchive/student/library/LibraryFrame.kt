package kr.co.gcflarchive.student.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kr.co.gcflarchive.core.net.LoginRequiredException
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PageHeader
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.userMessage

/**
 * Loads data once (and again after login / pull-to-refresh) for the archive pages.
 * [fetch] runs on every reload; the result is exposed as [LoadState].
 */
class LoadState<T>(val data: T?, val error: Throwable?, val loading: Boolean)

@Composable
fun <T> rememberLoad(vararg keys: Any?, fetch: suspend () -> T): Pair<LoadState<T>, () -> Unit> {
    val app = LocalApp.current
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(LoadState<T>(null, null, true)) }
    LaunchedEffect(reload, app.sessionVersion, *keys) {
        state = LoadState(state.data, null, true)
        state = runCatching { fetch() }.fold({ LoadState(it, null, false) }, { LoadState(state.data, it, false) })
    }
    return state to { reload++ }
}

/**
 * Archive page frame (past-exams.html / documents.html / photo.html / video.html):
 * title → description → search → chips → "총 N건" → list. Filtering happens on the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LibraryFrame(
    title: String,
    desc: String,
    searchHint: String,
    load: LoadState<T>,
    onReload: () -> Unit,
    onQuery: (String) -> Unit,
    total: String?,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    filters: @Composable ColumnScope.() -> Unit = {},
    content: LazyListScope.(T) -> Unit,
) {
    val app = LocalApp.current
    var input by remember { mutableStateOf("") }
    LaunchedEffect(input) {
        delay(200)
        onQuery(input.trim())
    }
    ScreenScaffold(title, actions = actions) {
        PullToRefreshBox(isRefreshing = load.loading, onRefresh = onReload, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PageHeader(title, desc)
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(searchHint) },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = { if (input.isNotEmpty()) IconButton(onClick = { input = "" }) { Icon(Icons.Filled.Clear, "지우기") } },
                            shape = RoundedCornerShape(8.dp),
                        )
                        filters()
                        if (total != null) Text(total, style = KrdsText.meta)
                    }
                }
                val data = load.data
                val error = load.error
                when {
                    error is LoginRequiredException && data == null -> item {
                        StateMessage("로그인이 필요합니다.", "재학생 인증 후 이용할 수 있어요.", "로그인", { app.requireLogin() }, locked = true)
                    }
                    error != null && data == null -> item {
                        StateMessage("불러오지 못했습니다.", error.userMessage(), "다시 시도", onReload)
                    }
                    data != null -> content(data)
                }
            }
        }
    }
}

/** Empty-result message for filtered lists. */
fun LazyListScope.emptyItem(show: Boolean, query: String) {
    if (show) item {
        StateMessage("자료가 없습니다.", if (query.isBlank()) "아직 등록된 자료가 없어요." else "다른 검색어로 찾아보세요.")
    }
}

// ── KRDS "상세 정보" modal as a bottom sheet ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(heading: String, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Krds.colors.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(heading, style = KrdsText.label.copy(color = Krds.colors.primaryText))
            Text(title, style = KrdsText.sectionTitle)
            content()
        }
    }
}

@Composable
fun DetailRow(label: String, value: String?, valueColor: Color? = null) {
    if (value.isNullOrBlank()) return
    Column {
        Text(label, style = KrdsText.label)
        SelectionContainer { Text(value, style = KrdsText.body.copy(color = valueColor ?: Krds.colors.text)) }
    }
}

@Composable
fun DetailPair(label1: String, value1: String?, label2: String, value2: String?) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) { DetailRow(label1, value1?.ifBlank { "-" } ?: "-") }
        Spacer(Modifier.padding(4.dp))
        Column(Modifier.weight(1f)) { DetailRow(label2, value2?.ifBlank { "-" } ?: "-") }
    }
}

@Composable
fun DetailInfo(label: String, text: String?) {
    if (text.isNullOrBlank()) return
    Column {
        Text(label, style = KrdsText.label, modifier = Modifier.padding(bottom = 4.dp))
        SelectionContainer {
            Text(
                text,
                style = KrdsText.body.copy(fontSize = KrdsText.meta.fontSize * 1.1f),
                modifier = Modifier.fillMaxWidth().background(Krds.colors.surfaceRaised, RoundedCornerShape(8.dp)).padding(12.dp),
            )
        }
    }
}

/** Badges that wrap onto the next line instead of being clipped. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BadgeRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}
