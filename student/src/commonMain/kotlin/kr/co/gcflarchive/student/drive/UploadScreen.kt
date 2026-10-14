package kr.co.gcflarchive.student.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.drive.DriveLogic
import kr.co.gcflarchive.core.drive.UploadResult
import kr.co.gcflarchive.core.net.UploadFile
import kr.co.gcflarchive.core.util.formatBytes
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Tab
import kr.co.gcflarchive.student.platform.shareText
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.InfoBox
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.SecondaryButton

/** Confirms and uploads picked (or shared-in) files to 극플드라이브. */
@Composable
fun UploadScreen(files: List<UploadFile>) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val invalid = remember(files) { DriveLogic.validate(files.map { it.name to it.bytes.size.toLong() }) }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<UploadResult?>(null) }

    fun start() {
        uploading = true
        progress = 0f
        result = null
        scope.launch {
            val r = app.services.drive.upload(files) { progress = it }
            uploading = false
            result = r
            if (r is UploadResult.NeedLogin) app.requireLogin()
        }
    }

    ScreenScaffold("극플드라이브에 올리기") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                files.forEachIndexed { i, f ->
                    if (i > 0) Hairline()
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, null, tint = Krds.colors.primaryText, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(f.name, style = KrdsText.body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(formatBytes(f.bytes.size.toLong()), style = KrdsText.meta, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Text("총 ${files.size}개 · ${formatBytes(files.sumOf { it.bytes.size.toLong() })}", style = KrdsText.meta)

            if (invalid != null) InfoBox(invalid, icon = Icons.Filled.ErrorOutline)

            when (val r = result) {
                is UploadResult.Success -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Krds.colors.successFg)
                        Spacer(Modifier.width(8.dp))
                        Text(if (r.zipped) "여러 파일을 ZIP 하나로 묶어 올렸어요." else "업로드했어요.", style = KrdsText.cardTitle)
                    }
                    r.files.forEach { up ->
                        KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(up.name, style = KrdsText.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(up.shortUrl, style = KrdsText.meta.copy(color = Krds.colors.primaryText))
                                }
                                IconButton(onClick = { clipboard.setText(AnnotatedString(up.shortUrl)); app.toast("링크를 복사했어요.") }) {
                                    Icon(Icons.Filled.ContentCopy, "링크 복사")
                                }
                                IconButton(onClick = { shareText("${up.name}\n${up.shortUrl}") }) { Icon(Icons.Filled.Share, "공유") }
                            }
                        }
                    }
                    PrimaryButton("드라이브로 가기", {
                        app.stack.clear()
                        app.tab = Tab.DRIVE
                    }, Modifier.fillMaxWidth())
                }
                is UploadResult.Failure -> InfoBox(r.message, icon = Icons.Filled.ErrorOutline)
                UploadResult.NeedLogin -> InfoBox("로그인 후 다시 올려주세요.", icon = Icons.Filled.ErrorOutline)
                null -> Unit
            }

            if (uploading) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Krds.colors.primary, trackColor = Krds.colors.surfaceRaised)
                Text("올리는 중… ${(progress * 100).toInt()}%", style = KrdsText.meta)
            }
            if (result !is UploadResult.Success) {
                PrimaryButton(
                    if (result == null) "올리기" else "다시 올리기",
                    ::start,
                    Modifier.fillMaxWidth(),
                    enabled = invalid == null && !uploading,
                )
                SecondaryButton("취소", { app.pop() }, Modifier.fillMaxWidth(), enabled = !uploading)
            }
            InfoBox("올린 파일은 7일간 보관되며, 학교 컴퓨터에서 드라이브 주소로 내려받을 수 있어요.")
        }
    }
}
