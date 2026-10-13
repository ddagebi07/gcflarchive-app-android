package kr.co.gcflarchive.student

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Desktop preview of the shared UI: ./gradlew :student:run (phone-sized window). */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "GCFL 아카이브", state = rememberWindowState(width = 420.dp, height = 860.dp)) {
        StudentApp()
    }
}
