package kr.co.gcflarchive.student.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.co.gcflarchive.student.LocalApp

/** Readable line length on iPad / tablets: content is centred and capped at 760dp. */
val MaxContentWidth = 760.dp

fun Modifier.contentWidth(): Modifier = this.fillMaxWidth().widthIn(max = MaxContentWidth)

/** A pushed screen: top bar with ←, snackbar, content centred on wide screens. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = LocalApp.current.let { app -> { app.pop() } },
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val app = LocalApp.current
    Scaffold(
        containerColor = Krds.colors.bg,
        topBar = { KrdsTopBar(title, onBack, actions) },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(app.snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.contentWidth().fillMaxSize()) { content(PaddingValues(0.dp)) }
        }
    }
}
