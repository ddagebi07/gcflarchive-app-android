package kr.co.gcflarchive.student

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kr.co.gcflarchive.core.prefs.AppPrefs
import kr.co.gcflarchive.core.util.AppLockPolicy
import kr.co.gcflarchive.student.platform.AppForegroundEffect
import kr.co.gcflarchive.student.platform.authenticateUser
import kr.co.gcflarchive.student.platform.canAuthenticateUser
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsText
import kr.co.gcflarchive.student.ui.PrimaryButton

/** 설정의 '앱을 열 때 생체 인증'이 켜져 있으면, 앱을 열거나 돌아올 때 잠금 화면을 먼저 보여 줘요. */
object AppLock {
    private val policy = AppLockPolicy()

    /** Compose-visible copy of [AppLockPolicy.locked]. */
    var locked by mutableStateOf(true)
        private set

    /** After turning the lock on in 설정 (or a successful unlock) the current session stays open. */
    fun markUnlocked() {
        policy.onUnlocked()
        locked = false
    }

    internal fun onForeground() {
        policy.onForeground(Clock.System.now().toEpochMilliseconds())
        locked = policy.locked
    }

    internal fun onBackground() = policy.onBackground(Clock.System.now().toEpochMilliseconds())
}

/** Draws the lock screen over everything while the app is locked. */
@Composable
fun AppLockGate(prefs: AppPrefs) {
    AppForegroundEffect(onForeground = { AppLock.onForeground() }, onBackground = { AppLock.onBackground() })
    if (prefs.appLockEnabled && AppLock.locked) LockScreen(prefs)
}

@Composable
private fun LockScreen(prefs: AppPrefs) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Face ID·Touch ID나 기기 암호로 확인해주세요.") }
    fun unlock() {
        if (!canAuthenticateUser()) {
            // The device lock was removed after the app lock was turned on: nothing to check against.
            prefs.appLockEnabled = false
            AppLock.markUnlocked()
            return
        }
        scope.launch {
            if (authenticateUser("GCFL 아카이브 잠금을 풀어요")) AppLock.markUnlocked()
            else message = "잠금을 풀려면 다시 확인해주세요."
        }
    }
    LaunchedEffect(Unit) { unlock() }
    Box(
        Modifier.fillMaxSize()
            .background(Krds.colors.bg)
            // Swallows touches so nothing underneath can be used.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Fingerprint, null, tint = Krds.colors.primaryText, modifier = Modifier.size(56.dp))
            Text("앱이 잠겨 있어요", style = KrdsText.pageTitle)
            Text(message, style = KrdsText.body.copy(color = Krds.colors.textSecondary), textAlign = TextAlign.Center)
            PrimaryButton("잠금 풀기", { unlock() }, Modifier.padding(top = 12.dp))
        }
    }
}
