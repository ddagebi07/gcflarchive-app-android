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
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.Services
import kr.co.gcflarchive.core.auth.LoginState
import kr.co.gcflarchive.core.net.UploadFile

enum class Tab(val label: String) { HOME("홈"), SEARCH("통합검색"), MEAL("급식"), DRIVE("극플드라이브"), SETTINGS("설정") }

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
        scope.launch { login = services.auth.state() }
    }

    /** Called by the login screen when the session is ready. */
    fun onLoggedIn() {
        if (stack.lastOrNull() == Route.Login) pop()
        sessionVersion++
        refreshLogin()
    }

    fun logout() {
        scope.launch {
            services.auth.logout()
            login = LoginState.LoggedOut
            sessionVersion++
            toast("로그아웃되었습니다.")
        }
    }

    fun toast(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
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
            "/search", "/archive" -> { stack.clear(); tab = Tab.SEARCH }
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

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState not provided") }
