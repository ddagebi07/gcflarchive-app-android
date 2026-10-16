package kr.co.gcflarchive.student.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.Services
import kr.co.gcflarchive.core.i18n.I18n
import kr.co.gcflarchive.student.platform.deviceLanguage
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PrimaryButton
import kr.co.gcflarchive.student.ui.SecondaryButton

/**
 * 처음 실행할 때 한 번: 기기 언어가 한국어면 그대로 한국어, 아니면 한국어 / English 중에서 골라요.
 * 두 언어로 함께 안내하고, 설정 > 화면 언어에서 언제든 바꿀 수 있다고 알려요.
 */
@Composable
fun FirstLanguageQuestion(services: Services) {
    var ask by remember { mutableStateOf(I18n.needsFirstChoice(deviceLanguage)) }
    if (!ask) return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun pick(code: String) {
        busy = true
        scope.launch {
            runCatching { I18n.choose(services.site, code) }
            ask = false
        }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("언어 / Language") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("앱에서 쓸 언어를 골라주세요.\nChoose the language for the app.", style = KrdsText.body)
                Text("설정 > 화면 언어에서 언제든 바꿀 수 있어요.\nYou can change it anytime in Settings > Display language.", style = KrdsText.meta)
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("English", { pick("en") }, Modifier.fillMaxWidth(), enabled = !busy)
                SecondaryButton("한국어", { pick(I18n.DEFAULT) }, Modifier.fillMaxWidth(), enabled = !busy)
            }
        },
    )
}
