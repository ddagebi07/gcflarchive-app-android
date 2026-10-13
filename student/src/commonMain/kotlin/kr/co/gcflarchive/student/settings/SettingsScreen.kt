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
import androidx.compose.material3.Text
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
import kr.co.gcflarchive.core.meal.MealKind
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.meal.MealAlarms
import kr.co.gcflarchive.student.platform.Notifications
import kr.co.gcflarchive.student.platform.platformName
import kr.co.gcflarchive.student.ui.Hairline
import kr.co.gcflarchive.student.ui.InfoBox
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PageHeader
import kr.co.gcflarchive.student.ui.contentWidth

const val APP_VERSION = "2.0.0"

/** 설정 tab: account, 급식 알림, links and version. */
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

    fun changed() {
        version++
        scope.launch { MealAlarms.reschedule(app.services) }
    }

    @Suppress("UNUSED_VARIABLE") val v = version
    val enabled = prefs.mealNotifyEnabled

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.contentWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader("설정")

            Section("계정")
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                when (val s = app.login) {
                    is LoginState.LoggedIn -> {
                        SettingRow("로그인됨", s.userId)
                        Hairline()
                        SettingRow("로그아웃", danger = true, onClick = { app.logout() })
                    }
                    else -> SettingRow("학번으로 로그인", "기출 정답지·드라이브·사진 이용", onClick = { app.requireLogin() })
                }
            }

            Section("급식 알림")
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("매일 급식 알림 받기", style = KrdsText.body)
                        Text("학교 가는 날 정해진 시간에 메뉴를 알려 드려요.", style = KrdsText.meta)
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
                SettingRow("알림 시간", prefs.mealNotifyTimeLabel, enabled = enabled, onClick = { pickTime = true })
                Hairline()
                SettingRow("알림 받을 식사", MealKind.label(prefs.mealNotifyKinds), enabled = enabled, onClick = { pickKind = true })
            }
            if (enabled && permissionDenied) {
                InfoBox("알림 권한이 꺼져 있어요. 기기의 설정 앱에서 GCFL 아카이브 알림을 허용해 주세요.", icon = Icons.Filled.NotificationsOff)
            }

            Section("정보")
            KrdsCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                SettingRow("웹사이트 열기", Config.HOST, onClick = { uri.openUri(Config.BASE_URL) })
                Hairline()
                SettingRow("버전", "$APP_VERSION · $platformName")
            }
            Text("${Config.SCHOOL_NAME} 학생 아카이브", style = KrdsText.meta, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(prefs.mealNotifyMinuteOfDay / 60, prefs.mealNotifyMinuteOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("알림 시간") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.mealNotifyMinuteOfDay = state.hour * 60 + state.minute
                    pickTime = false
                    changed()
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("취소") } },
        )
    }
    if (pickKind) {
        var kinds by remember { mutableStateOf(prefs.mealNotifyKinds) }
        AlertDialog(
            onDismissRequest = { pickKind = false },
            title = { Text("알림 받을 식사") },
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
                        Text("선택한 식사가 없으면 급식 알림이 오지 않습니다.", style = KrdsText.meta, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickKind = false }) { Text("확인") } },
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
