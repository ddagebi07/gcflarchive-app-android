package kr.co.gcflarchive.student.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.archive.PreviewKinds
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.library.rememberLoad
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.Loading
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.download

/** 통합검색 첨부파일 미리보기: PDF는 쪽 이미지, 그림은 그대로, 글은 텍스트로 (워터마크 없음). */
@Composable
fun PreviewScreen(api: String, kind: String, filename: String, downloadUrl: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    ScreenScaffold(
        title = filename,
        actions = {
            IconButton(onClick = { scope.launch { app.download(downloadUrl, filename) } }) { Icon(Icons.Filled.Download, "내려받기") }
        },
    ) {
        when (kind) {
            PreviewKinds.PDF -> {
                val (load, reload) = rememberLoad(api) { app.services.archive.previewPages(api) }
                val pages = load.data
                when {
                    pages != null -> LazyColumn(
                        Modifier.fillMaxSize().background(Krds.colors.surfaceRaised),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(pages) { i, (w, h) ->
                            SubcomposeAsyncImage(
                                model = Config.url("$api/page/${i + 1}.png"),
                                contentDescription = "${i + 1}쪽",
                                contentScale = ContentScale.FillWidth,
                                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                                modifier = Modifier.fillMaxWidth().aspectRatio(if (h > 0) w / h else 0.707f).background(Color.White),
                            )
                        }
                    }
                    load.error != null -> StateMessage("미리보기를 열지 못했어요.", "내려받아서 확인해주세요.", "다시 불러오기", reload)
                    else -> Loading()
                }
            }
            PreviewKinds.IMAGE -> Box(Modifier.fillMaxSize().background(Krds.colors.surfaceRaised).padding(12.dp), contentAlignment = Alignment.TopCenter) {
                SubcomposeAsyncImage(
                    model = Config.url("$api/raw"),
                    contentDescription = filename,
                    contentScale = ContentScale.FillWidth,
                    loading = { Loading() },
                    error = { StateMessage("미리보기를 열지 못했어요.", "내려받아서 확인해주세요.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PreviewKinds.TEXT -> {
                val (load, reload) = rememberLoad(api) { app.services.archive.previewText(api) }
                when {
                    load.data != null -> SelectionContainer {
                        Text(load.data!!, style = KrdsText.body, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp))
                    }
                    load.error != null -> StateMessage("미리보기를 열지 못했어요.", "내려받아서 확인해주세요.", "다시 불러오기", reload)
                    else -> Loading()
                }
            }
            else -> StateMessage("이 파일은 미리 볼 수 없어요.", "내려받아서 확인해주세요.")
        }
    }
}
