package kr.co.gcflarchive.student.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import kotlinx.datetime.toLocalDateTime
import kr.co.gcflarchive.core.map.NicknameStatus
import kr.co.gcflarchive.core.map.MapLogic
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.auth.LoginState
import kr.co.gcflarchive.core.i18n.I18n
import kr.co.gcflarchive.core.i18n.Language
import kr.co.gcflarchive.core.meal.MealKind
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.school.TimetableNames
import kr.co.gcflarchive.student.meal.MealAlarms
import kr.co.gcflarchive.student.platform.Notifications
import kr.co.gcflarchive.student.platform.authenticateUser
import kr.co.gcflarchive.student.platform.canAuthenticateUser
import kr.co.gcflarchive.student.AppLock
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.platform.platformName
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.InfoBox
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.tr
import kr.co.gcflarchive.student.ui.ThemeMode
import kr.co.gcflarchive.core.prefs.AppPrefs

const val APP_VERSION = "1.0.5"  // gradle.properties의 gcflVersion과 같게

/** 설정 (더보기 › 설정): account, 급식 알림, 앱 잠금, links and version. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val prefs = app.services.prefs
    // Re-read prefs after each change.
    var version by remember { mutableIntStateOf(0) }
    var permissionDenied by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickKind by remember { mutableStateOf(false) }
    var lockMessage by remember { mutableStateOf<String?>(null) }
    var nickname by remember { mutableStateOf<NicknameStatus?>(null) }
    var editNickname by remember { mutableStateOf(false) }
    LaunchedEffect(app.login) {
        nickname = if (app.login is LoginState.LoggedIn) runCatching { app.services.map.nickname() }.getOrNull() else null
    }
    // 화면 언어: only teachers can choose (the server says so), and only when there is more than 한국어.
    var languages by remember { mutableStateOf<List<Language>>(emptyList()) }
    var pickLanguage by remember { mutableStateOf(false) }
    var pickTheme by remember { mutableStateOf(false) }
    LaunchedEffect(app.login) {
        if (app.login is LoginState.LoggedIn) I18n.sync(app.services.site)
        languages = if (I18n.canChoose && I18n.languages.size > 1) I18n.languages else emptyList()
    }

    fun changed() {
        version++
        scope.launch { MealAlarms.reschedule(app.services) }
    }

    @Suppress("UNUSED_VARIABLE") val v = version
    val enabled = prefs.mealNotifyEnabled

    ScreenScaffold(tr("설정")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Section(tr("계정"))
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                when (val s = app.login) {
                    is LoginState.LoggedIn -> {
                        SettingRow(tr("로그인됨"), s.userId)
                        Hairline()
                        // 지도 후기와 커뮤니티가 함께 쓰는 닉네임.
                        SettingRow(tr("닉네임"), nickname?.nickname?.ifBlank { null } ?: tr("정하지 않음"), onClick = { editNickname = true })
                        Hairline()
                        SettingRow(tr("로그아웃하기"), danger = true, onClick = { app.logout() })
                    }
                    else -> SettingRow(tr("학번으로 로그인하기"), tr("기출 정답지·드라이브·사진"), onClick = { app.requireLogin() })
                }
            }

            Section(tr("화면"))
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                SettingRow(tr("화면 모드"), AppPrefs.themeLabel(ThemeMode.mode), onClick = { pickTheme = true })
            }

            Section(tr("공지 알림"))
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("새 공지 알림 받기"), style = KrdsText.body)
                        Text(tr("관리자가 알림을 보낸 공지가 올라오면 알려 드려요. 조금 늦게 올 수 있어요."), style = KrdsText.meta)
                    }
                    Switch(
                        checked = prefs.noticeNotifyEnabled,
                        onCheckedChange = { on ->
                            prefs.noticeNotifyEnabled = on
                            if (on) scope.launch { permissionDenied = !Notifications.requestPermission(); changed() } else changed()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Krds.colors.primary),
                    )
                }
            }

            Section(tr("급식 알림"))
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("매일 급식 알림 받기"), style = KrdsText.body)
                        Text(tr("학교 가는 날 정한 시간에 메뉴를 알려드려요."), style = KrdsText.meta)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            prefs.mealNotifyEnabled = on
                            if (on) scope.launch {
                                permissionDenied = !Notifications.requestPermission()
                                changed()
                            } else {
                                permissionDenied = false
                                changed()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Krds.colors.primary),
                    )
                }
                Hairline()
                SettingRow(tr("알림 시간"), prefs.mealNotifyTimeLabel, enabled = enabled, onClick = { pickTime = true })
                Hairline()
                SettingRow(tr("알림 받을 식사"), MealKind.label(prefs.mealNotifyKinds), enabled = enabled, onClick = { pickKind = true })
            }
            if (enabled && permissionDenied) {
                InfoBox(tr("알림이 꺼져 있어요. 기기의 설정 앱에서 GCFL 아카이브 알림을 켜주세요."), icon = Icons.Filled.NotificationsOff)
            }

            Section(tr("학교생활"))
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                if (languages.isNotEmpty()) {
                    SettingRow(tr("화면 언어"), languages.firstOrNull { it.code == I18n.lang }?.name ?: I18n.lang, onClick = { pickLanguage = true })
                    Hairline()
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("시간표에 과목 전체 이름 보기"), style = KrdsText.body)
                        Text(tr("끄면 줄임말(예: 심F), 켜면 전체 이름(예: 심화영어 Ⅱ)으로 보여요."), style = KrdsText.meta)
                    }
                    Switch(
                        checked = TimetableNames.full(prefs),
                        onCheckedChange = { TimetableNames.set(prefs, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Krds.colors.primary),
                    )
                }
            }

            Section(tr("보안"))
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("앱을 열 때 생체 인증"), style = KrdsText.body)
                        Text(tr("앱을 열거나 1분 넘게 다른 앱을 쓰다 돌아오면 Face ID·Touch ID나 기기 암호로 확인해요."), style = KrdsText.meta)
                    }
                    Switch(
                        checked = prefs.appLockEnabled,
                        onCheckedChange = { on ->
                            // Turning it on or off both need the device owner, so nobody else can switch it off.
                            if (!canAuthenticateUser()) {
                                lockMessage = tr("기기에 Face ID·Touch ID나 암호를 먼저 설정해주세요.")
                                return@Switch
                            }
                            scope.launch {
                                if (authenticateUser(if (on) tr("앱 잠금을 켜요") else tr("앱 잠금을 꺼요"))) {
                                    prefs.appLockEnabled = on
                                    AppLock.markUnlocked()
                                    lockMessage = null
                                    version++
                                    app.toast(if (on) tr("앱 잠금을 켰어요.") else tr("앱 잠금을 껐어요."))
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Krds.colors.primary),
                    )
                }
            }
            lockMessage?.let { InfoBox(it) }

            Section(tr("정보"))
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                SettingRow(tr("웹사이트에서 보기"), Config.HOST, onClick = { uri.openUri(Config.BASE_URL) })
                Hairline()
                SettingRow("버전", "$APP_VERSION · $platformName")
            }
            Text("${Config.SCHOOL_NAME} 학생 아카이브", style = KrdsText.meta, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (editNickname) {
        NicknameEditor(nickname, onDismiss = { editNickname = false }) { saved ->
            editNickname = false
            nickname = nickname?.copy(nickname = saved, canChange = false) ?: NicknameStatus(saved, false, "", 7)
            app.toast("닉네임을 '$saved'(으)로 정했어요.")
        }
    }
    if (pickTheme) {
        AlertDialog(
            onDismissRequest = { pickTheme = false },
            title = { Text(tr("화면 모드")) },
            text = {
                Column {
                    AppPrefs.THEME_MODES.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = m == ThemeMode.mode, role = Role.RadioButton) {
                                ThemeMode.set(m)
                                pickTheme = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = m == ThemeMode.mode, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Krds.colors.primary))
                            Text(AppPrefs.themeLabel(m), style = KrdsText.body, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickTheme = false }) { Text(tr("닫기")) } },
        )
    }
    if (pickLanguage) {
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pickLanguage = false },
            title = { Text(tr("화면 언어")) },
            text = {
                Column {
                    Text(tr("학교생활의 선생님 화면과 알림에 쓰여요."), style = KrdsText.meta, modifier = Modifier.padding(bottom = 8.dp))
                    languages.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = l.code == I18n.lang, enabled = !busy, role = Role.RadioButton) {
                                busy = true
                                scope.launch {
                                    runCatching { I18n.choose(app.services.site, l.code) }
                                        .onSuccess { pickLanguage = false; app.contentChanged() }
                                        .onFailure { app.toast(it.message ?: tr("처리하지 못했어요. 잠시 후 다시 시도해주세요.")) }
                                    busy = false
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = l.code == I18n.lang, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Krds.colors.primary))
                            Text(l.name, style = KrdsText.body, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickLanguage = false }) { Text(tr("닫기")) } },
        )
    }
    if (pickTime) {
        val state = rememberTimePickerState(prefs.mealNotifyMinuteOfDay / 60, prefs.mealNotifyMinuteOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(tr("알림 시간")) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.mealNotifyMinuteOfDay = state.hour * 60 + state.minute
                    pickTime = false
                    changed()
                }) { Text(tr("저장하기")) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(tr("닫기")) } },
        )
    }
    if (pickKind) {
        var kinds by remember { mutableStateOf(prefs.mealNotifyKinds) }
        AlertDialog(
            onDismissRequest = { pickKind = false },
            title = { Text(tr("알림 받을 식사")) },
            text = {
                // 중복 선택: none, either or both.
                Column {
                    MealKind.entries.forEach { k ->
                        val checked = k in kinds
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Checkbox) { on ->
                                kinds = if (on) kinds + k else kinds - k
                                prefs.mealNotifyKinds = kinds
                                changed()
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Krds.colors.primary))
                            Text(k.label, style = KrdsText.body, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    if (kinds.isEmpty()) {
                        Text(tr("고른 식사가 없으면 급식 알림이 오지 않아요."), style = KrdsText.meta, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickKind = false }) { Text(tr("닫기")) } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = KrdsText.label, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@Composable
private fun SettingRow(title: String, value: String? = null, enabled: Boolean = true, danger: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .let { if (onClick != null && enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = KrdsText.body.copy(color = when {
                !enabled -> Krds.colors.textDisabled
                danger -> Krds.colors.danger
                else -> Krds.colors.text
            }),
            modifier = Modifier.weight(1f),
        )
        if (value != null) Text(value, style = KrdsText.meta.copy(color = if (enabled) Krds.colors.textSecondary else Krds.colors.textDisabled))
    }
}

/** 닉네임 정하기·바꾸기: 지도 후기와 커뮤니티에 함께 보이고, 바꾸면 예전 후기·글·댓글에도 새 이름이 보여요. */
@Composable
private fun NicknameEditor(status: NicknameStatus?, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(status?.nickname.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val days = status?.changeDays ?: 7
    val locked = status != null && status.nickname.isNotBlank() && !status.canChange
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (status?.nickname.isNullOrBlank()) tr("닉네임을 정해주세요") else tr("닉네임 바꾸기")) },
        text = {
            Column {
                Text(
                    if (locked) "닉네임은 ${days}일에 한 번 바꿀 수 있어요. ${nextChangeLabel(status!!.nextChangeAt)}부터 바꿀 수 있어요."
                    else "지도 후기와 커뮤니티에 함께 보여요. 바꾸면 예전에 쓴 후기·글·댓글에도 새 닉네임이 보여요. ${days}일에 한 번 바꿀 수 있어요.",
                    style = KrdsText.meta,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    enabled = !locked,
                    singleLine = true,
                    isError = error != null,
                    label = { Text(tr("닉네임")) },
                    modifier = Modifier.padding(top = 12.dp),
                )
                error?.let { Text(it, style = KrdsText.meta.copy(color = Krds.colors.danger), modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("닫기")) } },
        confirmButton = {
            if (!locked) TextButton(enabled = !saving, onClick = {
                val n = name.trim()
                error = MapLogic.nicknameError(n)
                if (error != null) return@TextButton
                saving = true
                scope.launch {
                    runCatching { app.services.map.setNickname(n) }
                        .onSuccess { onSaved(n) }
                        .onFailure { error = it.message ?: tr("닉네임을 저장하지 못했어요. 잠시 후 다시 시도해주세요.") }
                    saving = false
                }
            }) { Text(tr("저장하기")) }
        },
    )
}

/** "10월 21일 17:05" in Korean time, or the raw value. */
private fun nextChangeLabel(iso: String): String = runCatching {
    val t = kotlinx.datetime.Instant.parse(iso).toLocalDateTime(kotlinx.datetime.TimeZone.of("Asia/Seoul"))
    "${kr.co.gcflarchive.core.i18n.I18nDates.monthDay(t.monthNumber, t.dayOfMonth)} ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}.getOrDefault(iso)
