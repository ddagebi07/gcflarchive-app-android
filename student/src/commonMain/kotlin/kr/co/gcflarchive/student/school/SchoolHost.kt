package kr.co.gcflarchive.student.school

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kr.co.gcflarchive.core.Services
import kr.co.gcflarchive.core.school.SchoolRole
import kr.co.gcflarchive.student.AppState
import kr.co.gcflarchive.student.LocalApp
import kr.co.gcflarchive.student.Route
import kr.co.gcflarchive.student.RouteScreen
import kr.co.gcflarchive.student.platform.PlatformBackHandler
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsTheme

/**
 * 학교생활 on its own, for a host that isn't this app's main window (the Android user app's
 * SchoolActivity). [target] is a push target like "visit:12"; [onExit] closes the host.
 */
@Composable
fun SchoolHost(services: Services, startTab: String? = null, target: String? = null, onExit: () -> Unit) {
    KrdsTheme {
        val scope = rememberCoroutineScope()
        val app = remember { AppState(services, scope) }
        CompositionLocalProvider(LocalApp provides app) {
            LaunchedEffect(Unit) {
                app.refreshLogin()
                app.push(Route.School(startTab ?: "TIMETABLE"))
                target?.let { app.openSchoolTarget(it, SchoolRole.OTHER) }
            }
            PlatformBackHandler(enabled = true) { if (app.stack.size > 1) app.pop() else onExit() }
            Box(Modifier.fillMaxSize().background(Krds.colors.bg)) {
                app.stack.forEachIndexed { i, route ->
                    key(route, i) { Box(Modifier.fillMaxSize().background(Krds.colors.bg)) { RouteScreen(route) } }
                }
            }
            // The ← on the first screen pops the stack to empty: close the host then
            // (but not before the first screen was pushed).
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(app.stack.size) {
                if (app.stack.isNotEmpty()) shown = true else if (shown) onExit()
            }
        }
    }
}
