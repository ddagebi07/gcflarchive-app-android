package kr.co.gcflarchive.student

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.material3.SnackbarDuration
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.Services
import kr.co.gcflarchive.core.auth.LoginState
import kr.co.gcflarchive.core.net.UploadFile
import kr.co.gcflarchive.student.school.openSchoolTarget

enum class Tab(val label: String) { HOME("홈"), SEARCH("통합검색"), MEAL("급식"), DRIVE("극플드라이브"), MORE("더보기") }

/** Screens pushed over the tabs. */
sealed interface Route {
    data object Login : Route
    data class PostDetail(val board: String, val idx: String) : Route
    data object PastExams : Route
    data object Documents : Route
    data object Photos : Route
    data object Videos : Route
    data class Viewer(val filename: String, val title: String, val includeAnswers: Boolean) : Route
    data object Grade : Route
    data class Map(val placeId: String? = null, val placeName: String? = null) : Route
    data object Notices : Route
    data class NoticeDetail(val id: Int) : Route
    data class Upload(val files: List<UploadFile>) : Route
    data object Settings : Route
    data object MyScores : Route
    data object Community : Route
    data object Surveys : Route
    data class Survey(val id: Int) : Route
    /** id 0 = new survey. */
    data class SurveyBuild(val id: Int) : Route
    data class SurveyResults(val id: Int) : Route
    data class CommunityPost(val id: Int) : Route
    /** New post on [boardId], or edit [editId] (with its current [title] / [content]). */
    data class CommunityWrite(
        val boardId: String?,
        val editId: Int = 0,
        val title: String = "",
        val content: String = "",
        val images: List<kr.co.gcflarchive.core.community.CommunityImage> = emptyList(),
        val poll: kr.co.gcflarchive.core.community.CommunityPoll? = null,
    ) : Route
    /** 학교생활 (시간표·연락·질문). [tab] = SchoolTab name. */
    data class School(val tab: String = "TIMETABLE") : Route
    data class SchoolMessage(val id: Int) : Route
    data class SchoolThread(val id: Int) : Route
    data class SchoolVisit(val id: Int) : Route
    data class SchoolAsk(val teacherId: String) : Route
    data object SchoolCompose : Route
    /** id 0 = new list. */
    data class SchoolGroup(val id: Int) : Route
    /** 통합검색 첨부파일 미리보기 (pdf / image / text). */
    data class Preview(val api: String, val kind: String, val filename: String, val downloadUrl: String) : Route
}

/** App-wide state: current tab, pushed screens, login, snackbar. */
@Stable
class AppState(val services: Services, private val scope: CoroutineScope) {
    var tab by mutableStateOf(Tab.HOME)
    val stack = mutableStateListOf<Route>()
    var login by mutableStateOf<LoginState>(LoginState.Unknown)
        private set

    /** Bumped after login/logout so screens reload their data. */
    var sessionVersion by mutableIntStateOf(0)
        private set

    /** Bumped after posting / editing so open lists and posts reload. */
    var contentVersion by mutableIntStateOf(0)
        private set

    fun contentChanged() {
        contentVersion++
    }

    val snackbar = SnackbarHostState()

    val userId: String? get() = (login as? LoginState.LoggedIn)?.userId

    fun push(route: Route) {
        if (stack.lastOrNull() != route) stack.add(route)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun popTo(route: Route) {
        while (stack.isNotEmpty() && stack.last() != route) stack.removeAt(stack.lastIndex)
    }

    fun requireLogin() = push(Route.Login)

    fun refreshLogin() {
        scope.launch {
            val state = services.auth.state()
            when (state) {
                is LoginState.LoggedIn -> services.prefs.signedIn = true
                LoginState.LoggedOut -> services.prefs.signedIn = false
                LoginState.Unknown -> {}
            }
            login = state
        }
    }

    /**
     * 로그인 관문: the app can't be used logged out, so the full-screen login covers everything.
     * Offline (Unknown), someone who logged in before keeps using the app.
     */
    val needsLogin: Boolean get() = when (login) {
        is LoginState.LoggedIn -> false
        LoginState.LoggedOut -> true
        LoginState.Unknown -> !services.prefs.signedIn
    }

    /** Called by the login screen when the session is ready. */
    fun onLoggedIn() {
        if (stack.lastOrNull() == Route.Login) pop()
        services.prefs.signedIn = true
        login = LoginState.Unknown  // hides the gate at once; refreshLogin fills in who
        sessionVersion++
        refreshLogin()
    }

    fun logout() {
        scope.launch {
            services.auth.logout()
            services.prefs.signedIn = false
            login = LoginState.LoggedOut
            sessionVersion++
            // No toast: the full-screen login appears right away, and a toast over it hid the 로그인 button.
        }
    }

    private var toastJob: Job? = null

    /** A short message (2s). A new one replaces the one on screen instead of queuing behind it. */
    fun toast(message: String) {
        toastJob?.cancel()
        snackbar.currentSnackbarData?.dismiss()
        toastJob = scope.launch {
            launch { snackbar.showSnackbar(message, duration = SnackbarDuration.Indefinite) }
            delay(2_000)
            snackbar.currentSnackbarData?.dismiss()
        }
    }

    /**
     * Opens a site link: pages with a native screen open in the app, everything else in
     * the browser (via [openExternal]).
     */
    fun openLink(url: String, openExternal: (String) -> Unit) {
        if (!Config.isOwnHost(url) && !url.startsWith("/")) return openExternal(url)
        val pathAndQuery = if (url.startsWith("/")) url else "/" + url.substringAfter("://").substringAfter('/', "")
        val path = pathAndQuery.substringBefore('?').removeSuffix(".html").trimEnd('/').ifEmpty { "/" }
        val query = pathAndQuery.substringAfter('?', "").split('&').mapNotNull {
            val k = it.substringBefore('=', "")
            if (k.isEmpty()) null else k to it.substringAfter('=', "")
        }.toMap()
        when (path) {
            "/" -> { stack.clear(); tab = Tab.HOME }
            "/search", "/archive" -> {
                stack.clear()
                tab = Tab.SEARCH
                // Shared archive links: /search?s=<board>:<idx> opens that post.
                val shared = query["s"]?.let { decodeQuery(it) }
                val board = query["share_board"] ?: shared?.substringBefore(':', "")
                val idx = query["share_idx"] ?: shared?.substringAfter(':', "")
                if (!board.isNullOrBlank() && !idx.isNullOrBlank()) push(Route.PostDetail(board, idx))
            }
            "/school" -> query["target"]?.let { openSchoolTarget(decodeQuery(it), kr.co.gcflarchive.core.school.SchoolRole.OTHER) }
                ?: push(Route.School(query["tab"]?.uppercase() ?: "TIMETABLE"))
            "/community" -> push(Route.Community)
            "/survey", "/surveys" -> query["id"]?.toIntOrNull()?.let { push(Route.Survey(it)) } ?: push(Route.Surveys)
            "/meal" -> { stack.clear(); tab = Tab.MEAL }
            "/share" -> { stack.clear(); tab = Tab.DRIVE }
            "/verify" -> push(Route.Login)
            "/past-exams" -> push(Route.PastExams)
            "/documents" -> push(Route.Documents)
            "/photo" -> push(Route.Photos)
            "/video" -> push(Route.Videos)
            "/grade-calculator", "/grade-consent" -> push(Route.Grade)
            "/map" -> push(Route.Map(query["placeId"], query["placeName"]?.replace("%20", " ")))
            "/notice" -> push(Route.Notices)
            "/notice-view" -> query["id"]?.toIntOrNull()?.let { push(Route.NoticeDetail(it)) } ?: push(Route.Notices)
            else -> openExternal(Config.url(pathAndQuery))
        }
    }
}

/** %XX and + decoding for one query value (UTF-8). */
private fun decodeQuery(value: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '%' && i + 2 <= value.lastIndex -> {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes += hex.toByte()
                    i += 3
                    continue
                }
                bytes += c.code.toByte()
            }
            c == '+' -> bytes += ' '.code.toByte()
            else -> c.toString().encodeToByteArray().forEach { bytes += it }
        }
        i++
    }
    return bytes.toByteArray().decodeToString()
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState not provided") }
