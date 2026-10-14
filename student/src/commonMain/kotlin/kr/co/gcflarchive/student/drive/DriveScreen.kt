package kr.co.gcflarchive.student.drive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kr.co.gcflarchive.core.drive.DriveFile
import kr.co.gcflarchive.core.drive.DriveFileStatus
import kr.co.gcflarchive.core.drive.DriveListing
import kr.co.gcflarchive.core.drive.DriveLogic
import kr.co.gcflarchive.core.meal.MealRepository
import kr.co.gcflarchive.core.net.LoginRequiredException
import kr.co.gcflarchive.core.util.fixed
import kr.co.gcflarchive.core.util.formatBytes
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.platform.rememberFilePicker
import kr.co.gcflarchive.student.platform.shareText
import kr.co.gcflarchive.student.ui.Badge
import kr.co.gcflarchive.student.ui.BadgeTone
import kr.co.gcflarchive.student.ui.InfoBox
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PageHeader
import kr.co.gcflarchive.student.ui.SecondaryButton
import kr.co.gcflarchive.student.ui.StateMessage
import kr.co.gcflarchive.student.ui.contentWidth
import kr.co.gcflarchive.student.ui.download
import kr.co.gcflarchive.student.ui.userMessage

/** 극플드라이브 tab: storage, PIN / drive address, upload and stored files (share.html). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var listing by remember { mutableStateOf<Result<DriveListing>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmDownload by remember { mutableStateOf<DriveFile?>(null) }
    var confirmDelete by remember { mutableStateOf<DriveFile?>(null) }
    var editPin by remember { mutableStateOf(false) }
    val pick = rememberFilePicker { files -> app.push(Route.Upload(files)) }

    // Reload on login/logout and when coming back from an upload (stack shrinks).
    LaunchedEffect(reload, app.sessionVersion, app.stack.size) {
        if (app.stack.isNotEmpty()) return@LaunchedEffect
        refreshing = true
        listing = runCatching { app.services.drive.list() }
        refreshing = false
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { reload++ }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.contentWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeader("극플드라이브", "학교 컴퓨터에서 인쇄할 파일을 잠시 맡겨 두세요.")
                val r = listing
                val error = r?.exceptionOrNull()
                when {
                    r == null -> Unit
                    error is LoginRequiredException -> StateMessage(
                        "로그인하면 이용할 수 있어요.", "극플드라이브는 재학생 인증 후 이용할 수 있어요.", "로그인", { app.requireLogin() }, locked = true,
                    )
                    error != null -> StateMessage("드라이브를 불러오지 못했어요.", error.userMessage(), "다시 불러오기", { reload++ })
                    else -> {
                        val data = r.getOrThrow()
                        val usage = DriveLogic.usage(data.files)
                        KrdsCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("저장 공간", style = KrdsText.cardTitle, modifier = Modifier.weight(1f))
                                Badge("${usage.count} / ${DriveLogic.MAX_FILES}개", if (usage.isFull) BadgeTone.DANGER else BadgeTone.INFO)
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { usage.percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = when {
                                    usage.percent >= 90 -> Krds.colors.danger
                                    usage.percent >= 70 -> Krds.colors.star
                                    else -> Krds.colors.primary
                                },
                                trackColor = Krds.colors.surfaceRaised,
                            )
                            Text("${(usage.bytes / 1048576.0).fixed(2)}MB / 10MB (${usage.percent}%)", style = KrdsText.meta, modifier = Modifier.padding(top = 6.dp))
                        }

                        KrdsCard {
                            Text("드라이브 주소", style = KrdsText.cardTitle)
                            val url = app.userId?.let { DriveLogic.driveUrl(it, data.pin) }
                            Text(url ?: "-", style = KrdsText.body.copy(color = Krds.colors.primaryText), modifier = Modifier.padding(top = 6.dp))
                            Text("PIN ${data.pin} · 학교 컴퓨터에서 위 주소를 열면 파일을 받을 수 있어요.", style = KrdsText.meta, modifier = Modifier.padding(top = 4.dp))
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryButton("주소 복사", {
                                    url?.let { clipboard.setText(AnnotatedString(it)); app.toast("주소를 복사했어요.") }
                                }, Modifier.weight(1f), icon = Icons.Filled.ContentCopy)
                                SecondaryButton("PIN 변경", { editPin = true }, Modifier.weight(1f))
                            }
                        }

                        Column(
                            Modifier.fillMaxWidth()
                                .border(BorderStroke(1.5.dp, Krds.colors.primary), RoundedCornerShape(12.dp))
                                .clickable(onClick = pick)
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Filled.UploadFile, null, tint = Krds.colors.primaryText, modifier = Modifier.size(36.dp))
                            Text("파일 선택해서 올리기", style = KrdsText.cardTitle.copy(color = Krds.colors.primaryText), modifier = Modifier.padding(top = 8.dp))
                            Text("PDF·한글·오피스·TXT·ZIP, 최대 5개 / 10MB", style = KrdsText.meta, modifier = Modifier.padding(top = 2.dp))
                        }

                        Text("보관 중인 파일", style = KrdsText.sectionTitle, modifier = Modifier.padding(top = 8.dp))
                        if (data.files.isEmpty()) InfoBox("아직 올린 파일이 없어요.")
                        data.files.forEach { f ->
                            FileCard(
                                f,
                                onDownload = { confirmDownload = f },
                                onShare = { shareText("${f.filename}\n${f.shortUrl}") },
                                onDelete = { confirmDelete = f },
                            )
                        }
                        InfoBox("파일은 올린 뒤 7일간 보관되고, 한 번 내려받으면 5분 뒤 자동으로 삭제돼요.")
                    }
                }
            }
        }
    }

    confirmDownload?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDownload = null },
            title = { Text("파일을 내려받을까요?") },
            text = { Text("내려받으면 5분 뒤 드라이브에서 자동으로 삭제돼요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownload = null
                    scope.launch { app.download(f.shortUrl, f.filename); reload++ }
                }) { Text("내려받기") }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = null }) { Text("닫기") } },
        )
    }
    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("파일을 삭제할까요?") },
            text = { Text("‘${f.filename}’을(를) 삭제하면 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        try {
                            app.services.drive.delete(f.fileId)
                            app.toast("삭제했어요.")
                            reload++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            app.toast(e.userMessage())
                        }
                    }
                }) { Text("삭제", color = Krds.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("닫기") } },
        )
    }
    if (editPin) {
        PinDialog(current = listing?.getOrNull()?.pin.orEmpty(), onDismiss = { editPin = false }, onSaved = { editPin = false; reload++ })
    }
}

@Composable
private fun FileCard(file: DriveFile, onDownload: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(file.filename, style = KrdsText.cardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val uploaded = DriveLogic.parse(file.uploadedAt)?.toLocalDateTime(MealRepository.SEOUL)?.let {
                    "${it.monthNumber}/${it.dayOfMonth} ${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
                } ?: "-"
                Text("${formatBytes(file.fileSize)} · $uploaded", style = KrdsText.meta, modifier = Modifier.padding(top = 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    when (val s = DriveLogic.status(file)) {
                        is DriveFileStatus.Downloaded -> {
                            Badge("다운로드됨", BadgeTone.DANGER)
                            Text("  ${s.minutesLeft}분 뒤 삭제", style = KrdsText.meta.copy(color = Krds.colors.danger))
                        }
                        is DriveFileStatus.Stored -> {
                            Badge(if (file.quotaExempt) "기출" else "보관 중", if (file.quotaExempt) BadgeTone.PURPLE else BadgeTone.SUCCESS)
                            Text("  ${s.daysLeft}일 남음", style = KrdsText.meta)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, "내려받기", tint = Krds.colors.primaryText) }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "링크 공유", tint = Krds.colors.primaryText) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "삭제", tint = Krds.colors.danger) }
        }
    }
}

@Composable
private fun PinDialog(current: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN 변경") },
        text = {
            Column {
                Text("드라이브 주소에 쓰이는 숫자 4자리예요.", style = KrdsText.meta)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter(Char::isDigit).take(4); error = null },
                    singleLine = true,
                    label = { Text("새 PIN (숫자 4자리)") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (!DriveLogic.isValidPin(pin)) { error = "숫자 4자리를 입력해주세요."; return@TextButton }
                busy = true
                scope.launch {
                    try {
                        app.services.drive.setPin(pin)
                        app.toast("PIN을 변경했어요.")
                        onSaved()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.userMessage("PIN을 변경하지 못했어요.")
                    } finally {
                        busy = false
                    }
                }
            }) { Text("변경") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
