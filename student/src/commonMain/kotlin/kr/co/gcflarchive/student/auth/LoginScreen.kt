package kr.co.gcflarchive.student.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.auth.AuthApi
import kr.co.gcflarchive.core.net.ApiException
import kr.co.gcflarchive.core.net.LoginRequiredException
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.ui.InfoBox
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PageHeader
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.SecondaryButton

private const val TERMS_STUDENT = "1. 개인정보의 수집·이용 목적\n내신 시험의 예상 등급컷 산출 및 각종 자료 제공, 본인인증\n\n2. 수집하려는 개인정보의 항목\n· 사용자의 학번\n· 응시한 시험의 과목별 원점수(등급컷 서비스)\n\n3. 개인정보의 보유 및 이용 기간\n· 사용자의 학번: 당해 학년도 종료일까지\n· 시험 원점수: 당해 평가 종료일로부터 30일\n\n4. 동의를 거부할 권리 및 불이익\n동의를 거부하실 수 있으며, 학번 제공을 거부하실 경우 서비스를 이용할 수 없어요. 수집한 원점수는 등급컷과 백분위 산출을 위한 자료로만 활용되며, 서버에 암호화되어 저장되므로 관리자는 개인의 학번과 점수 등을 열람할 수 없어요. 제공을 거부하실 경우 등급컷 관련 서비스를 이용할 수 없어요."
private const val TERMS_TEACHER = "1. 개인정보의 수집·이용 목적\n교사 계정 식별, 서비스 이용 확인, 연관 업무 연락\n\n2. 수집하려는 개인정보의 항목\n· 성명\n· 이메일 주소\n· STUCARE 아이디\n\n3. 개인정보의 보유 및 이용 기간\n계정이 유지되는 동안\n\n4. 동의를 거부할 권리 및 불이익\n동의를 거부하실 수 있어요. 거부하실 시 서비스 이용이 불가해요."

/**
 * Native login + first-login 개인정보 동의 (same APIs as verify.html).
 * [gate]: the full-screen login that covers the app until it's logged in (no back).
 */
@Composable
fun LoginScreen(gate: Boolean = false) {
    val app = LocalApp.current
    val auth = app.services.auth
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AuthApi.Mode.STUDENT) }
    var id by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var consent by remember { mutableStateOf<AuthApi.LoginResult?>(null) }
    var agreeRequired by remember { mutableStateOf(false) }
    var agreeScore by remember { mutableStateOf(false) }
    var teacherName by remember { mutableStateOf("") }
    var teacherEmail by remember { mutableStateOf("") }
    var forgot by remember { mutableStateOf(false) }

    fun message(e: Throwable) = when (e) {
        is LoginRequiredException, is ApiException -> e.message ?: "로그인하지 못했어요."
        else -> "인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요."
    }

    fun submit() {
        if (busy) return
        error = when {
            mode == AuthApi.Mode.STUDENT && !AuthApi.isValidHakbun(id.trim()) -> "학번 5자리를 숫자로 입력해주세요."
            mode == AuthApi.Mode.TEACHER && id.isBlank() -> "교사 ID를 입력해주세요."
            password.isBlank() -> "비밀번호를 입력해주세요."
            else -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            runCatching { auth.login(mode, id.trim(), password, rememberMe) }
                .onSuccess { r ->
                    password = ""
                    if (r.consentRequired) {
                        consent = r
                        teacherName = r.teacherName
                        teacherEmail = r.teacherEmail
                    } else {
                        app.toast("${r.teacherName.ifBlank { id.trim() }}님, 반가워요!")
                        app.onLoggedIn()
                    }
                }
                .onFailure { error = message(it) }
            busy = false
        }
    }

    fun cancelConsent() {
        // Declining must not leave a half-verified session behind.
        consent = null
        scope.launch { auth.logout() }
    }

    if (forgot) {
        FirstPasswordScreen(id, onBack = { forgot = false }) { h, p ->
            mode = AuthApi.Mode.STUDENT
            id = h
            password = p
            error = null
            forgot = false
        }
        return
    }

    val teacher = mode == AuthApi.Mode.TEACHER || consent?.userType == "teacher"
    ScreenScaffold(
        title = if (consent == null) "로그인" else "개인정보 수집·이용 동의",
        onBack = when {
            consent != null -> ({ cancelConsent(); if (!gate) app.pop() })
            gate -> null
            else -> ({ app.pop() })
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (consent == null) {
                PageHeader(
                    "로그인",
                    if (gate) "과천외고 학생·선생님만 쓸 수 있어요. STUCARE 계정으로 로그인해주세요."
                    else "STUCARE 계정으로 재학생인지 확인한 뒤 이용할 수 있어요. 계정은 다른 사람과 함께 쓰지 말아주세요.",
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    listOf(AuthApi.Mode.STUDENT to "학생 로그인", AuthApi.Mode.TEACHER to "교사 로그인").forEachIndexed { i, (m, label) ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m; id = ""; error = null },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
                OutlinedTextField(
                    value = id,
                    onValueChange = { v -> id = if (mode == AuthApi.Mode.STUDENT) v.filter(Char::isDigit).take(5) else v.trim() },
                    label = { Text(if (mode == AuthApi.Mode.STUDENT) "학번 5자리" else "교사 ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (mode == AuthApi.Mode.STUDENT) KeyboardType.NumberPassword else KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("스투케어 비밀번호") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "비밀번호 표시")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                CheckRow("로그인 유지", rememberMe) { rememberMe = it }
                error?.let { Text(it, color = Krds.colors.danger, style = KrdsText.body) }
                PrimaryButton(if (busy) "확인하고 있어요" else "권한 인증", ::submit, Modifier.fillMaxWidth(), enabled = !busy)
                if (mode == AuthApi.Mode.STUDENT) {
                    TextButton(onClick = { forgot = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("비밀번호를 잊었어요", color = Krds.colors.primaryText)
                    }
                }
                Text("비밀번호는 학교 STUCARE 인증에만 사용되며 앱에 저장되지 않아요.", style = KrdsText.meta)
            } else {
                Text("개인정보 보호법 제15조에 따라 아래 내용에 동의하셔야 서비스를 계속 이용하실 수 있어요.", style = KrdsText.pageDesc)
                InfoBox(if (teacher) TERMS_TEACHER else TERMS_STUDENT)
                if (teacher) {
                    OutlinedTextField(teacherName, { teacherName = it }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        teacherEmail, { teacherEmail = it.trim() }, label = { Text("이메일 주소") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                    )
                }
                CheckRow(if (teacher) "[필수] 정보 수집·이용에 동의해요." else "[필수] 학번 수집·이용에 동의해요.", agreeRequired) { agreeRequired = it }
                if (!teacher) CheckRow("[선택] 원점수 수집·이용에 동의해요. (거부 시 등급컷 관련 서비스 이용 불가)", agreeScore) { agreeScore = it }
                error?.let { Text(it, color = Krds.colors.danger, style = KrdsText.body) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("돌아가기", { cancelConsent() }, Modifier.weight(1f), enabled = !busy)
                    PrimaryButton("동의 후 계속", {
                        error = when {
                            !agreeRequired -> "동의하지 않으면 서비스를 이용할 수 없어요."
                            teacher && (teacherName.isBlank() || !AuthApi.isValidEmail(teacherEmail)) -> "이름과 이메일을 모두 입력해주세요."
                            else -> null
                        }
                        if (error == null) {
                            busy = true
                            scope.launch {
                                runCatching { if (teacher) auth.consentTeacher(teacherName.trim(), teacherEmail.trim()) else auth.consentStudent(agreeScore) }
                                    .onSuccess {
                                        app.toast("${teacherName.ifBlank { id.trim() }}님, 반가워요!")
                                        app.onLoggedIn()
                                    }
                                    .onFailure { error = message(it) }
                                busy = false
                            }
                        }
                    }, Modifier.weight(2f), enabled = !busy && agreeRequired)
                }
            }
        }
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label, style = KrdsText.body, modifier = Modifier.weight(1f))
    }
}
