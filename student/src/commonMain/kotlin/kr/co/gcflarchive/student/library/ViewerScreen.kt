package kr.co.gcflarchive.student.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.library.LibraryRepository
import kr.co.gcflarchive.core.library.ViewerInfo
import kr.co.gcflarchive.core.net.LoginRequiredException
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.Loading
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.download

/**
 * In-app PDF viewer: shows the server's watermarked page images
 * (/api/pdf-viewer/<file>/page/N.png), so protection stays on the server.
 * Pinch to zoom; one finger scrolls the pages.
 */
@Composable
fun ViewerScreen(filename: String, title: String, includeAnswers: Boolean) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val (load, reload) = rememberLoad { app.services.library.viewerInfo(filename, includeAnswers) }
    val info: ViewerInfo? = load.data
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val list = rememberLazyListState()
    val shownTitle = info?.title?.ifBlank { null } ?: title

    ScreenScaffold(
        title = shownTitle,
        actions = {
            if (scale != 1f) IconButton(onClick = { scale = 1f; offset = Offset.Zero }) { Icon(Icons.Filled.ZoomOutMap, "원래 크기") }
            IconButton(onClick = {
                scope.launch { app.download(LibraryRepository.downloadUrl(filename, includeAnswers), "$shownTitle.pdf") }
            }) { Icon(Icons.Filled.Download, "다운로드") }
        },
    ) {
        when {
            info != null -> Box(Modifier.fillMaxSize().background(Krds.colors.surfaceRaised)) {
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Only multi-touch gestures are handled here, so single-finger scrolling still works.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val newScale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                        val pan = event.calculatePan()
                                        val maxX = size.width * (newScale - 1f) / 2f
                                        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), 0f)
                                        scale = newScale
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        },
                ) {
                    itemsIndexed(info.pages) { i, (w, h) ->
                        SubcomposeAsyncImage(
                            model = Config.url(LibraryRepository.viewerPagePath(filename, i + 1, includeAnswers)),
                            contentDescription = "${i + 1}쪽",
                            contentScale = ContentScale.FillWidth,
                            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                            modifier = Modifier.fillMaxWidth().aspectRatio(if (h > 0) w / h else 0.707f).background(Color.White),
                        )
                    }
                }
                if (info.pages.isNotEmpty()) {
                    val page = (list.firstVisibleItemIndex + 1).coerceAtMost(info.pages.size)
                    Text(
                        "$page / ${info.pages.size}",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            load.error is LoginRequiredException -> StateMessage("로그인이 필요합니다.", "재학생 인증 후 볼 수 있는 자료입니다.", "로그인", { app.requireLogin() }, locked = true)
            load.error != null -> StateMessage("문서를 열지 못했습니다.", "잠시 후 다시 시도해 주세요.", "다시 시도", reload)
            else -> Loading()
        }
    }
}
