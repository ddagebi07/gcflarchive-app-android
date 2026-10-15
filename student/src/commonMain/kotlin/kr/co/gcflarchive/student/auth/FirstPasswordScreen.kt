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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.auth.AuthApi
import kr.co.gcflarchive.core.net.ApiException
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsCard
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PageHeader
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.ScreenScaffold
import kr.co.gcflarchive.student.ui.SecondaryButton

private const val CHANGE_URL = "https://www.stucare.net/stucare/gcfl/course/passchange.php"

/**
 * 비밀번호를 잊은 학생: 학번 + 이름으로 STUCARE 초기·리셋 비밀번호를 확인해요
 * (STUCARE의 myfirst.php와 같은 일, 서버 /api/first-password를 거쳐요).
 * [onUse] hands the 학번 and password back to the login form.
 */
@Composable
fun FirstPasswordScreen(initialHakbun: String, onBack: () -> Unit, onUse: (hakbun: String, password: String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var hakbun by remember { mutableStateOf(initialHakbun.takeIf { AuthApi.isValidHakbun(it) }.orEmpty()) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Triple<String, String, String>?>(null) } // 학번, 이름, 비밀번호

    fun submit() {
        if (busy) return
        error = when {
            !AuthApi.isValidHakbun(hakbun) -> "학번 5자리를 숫자로 입력해주세요."
            name.isBlank() -> "학생 본인의 이름을 입력해주세요."
            else -> null
        }
        if (error != null) return
        busy = true
        found = null
        scope.launch {
            runCatching { app.services.auth.firstPassword(hakbun, name.trim()) }
                .onSuccess { found = Triple(hakbun, name.trim(), it) }
                .onFailure { error = (it as? ApiException)?.message ?: "인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요." }
            busy = false
        }
    }

    ScreenScaffold(title = "초기 비밀번호 확인", onBack = onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageHeader("초기 비밀번호 확인", "학번과 이름을 입력하면 STUCARE의 초기 비밀번호(또는 리셋된 비밀번호)를 알려드려요. 학생만 쓸 수 있어요.")
            OutlinedTextField(
                value = hakbun, onValueChange = { hakbun = it.filter(Char::isDigit).take(5) },
                label = { Text("학번 5자리") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(20) },
                label = { Text("학생 이름") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = Krds.colors.danger, style = KrdsText.body) }
            PrimaryButton(if (busy) "확인하고 있어요" else "확인", ::submit, Modifier.fillMaxWidth(), enabled = !busy)
            found?.let { (h, n, password) ->
                KrdsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$n 학생의 비밀번호", style = KrdsText.cardTitle)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SelectionContainer(Modifier.weight(1f)) {
                                Text(password, style = KrdsText.body.copy(fontFamily = FontFamily.Monospace, fontSize = 24.sp, color = Krds.colors.textStrong))
                            }
                            SecondaryButton("복사", {
                                clipboard.setText(AnnotatedString(password))
                                app.toast("비밀번호를 복사했어요.")
                            })
                        }
                        Text("비밀번호를 바꿨다면 바꾼 비밀번호로 로그인해야 해요. 로그인한 뒤 다른 사람이 알 수 없는 비밀번호로 바꿔주세요.", style = KrdsText.meta)
                        PrimaryButton("이 비밀번호로 로그인하기", { onUse(h, password) }, Modifier.fillMaxWidth())
                        SecondaryButton("STUCARE에서 비밀번호 바꾸기", { uri.openUri(CHANGE_URL) }, Modifier.fillMaxWidth())
                    }
                }
            }
            Text("입력한 학번과 이름은 확인에만 쓰고 저장하지 않아요. 너무 여러 번 시도하면 잠시 막혀요.", style = KrdsText.meta)
        }
    }
}
