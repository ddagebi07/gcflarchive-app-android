package kr.co.gcflarchive.student

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.launch
import kr.co.gcflarchive.core.Services
import kr.co.gcflarchive.student.meal.MealAlarms
import kr.co.gcflarchive.student.auth.LoginScreen
import kr.co.gcflarchive.student.drive.DriveScreen
import kr.co.gcflarchive.student.drive.UploadScreen
import kr.co.gcflarchive.student.grade.GradeScreen
import kr.co.gcflarchive.student.home.HomeScreen
import kr.co.gcflarchive.student.library.DocumentsScreen
import kr.co.gcflarchive.student.library.PastExamsScreen
import kr.co.gcflarchive.student.library.PhotosScreen
import kr.co.gcflarchive.student.library.VideosScreen
import kr.co.gcflarchive.student.library.ViewerScreen
import kr.co.gcflarchive.student.map.MapScreen
import kr.co.gcflarchive.student.meal.MealScreen
import kr.co.gcflarchive.student.notice.NoticeDetailScreen
import kr.co.gcflarchive.student.notice.NoticeAlerts
import kr.co.gcflarchive.student.notice.NoticeListScreen
import kr.co.gcflarchive.student.platform.AppForegroundEffect
import kr.co.gcflarchive.student.platform.PlatformBackHandler
import kr.co.gcflarchive.student.search.PostDetailScreen
import kr.co.gcflarchive.student.search.SearchScreen
import kr.co.gcflarchive.student.settings.SettingsScreen
import kr.co.gcflarchive.student.more.MoreScreen
import kr.co.gcflarchive.student.community.CommunityScreen
import kr.co.gcflarchive.student.community.CommunityPostScreen
import kr.co.gcflarchive.student.community.CommunityWriteScreen
import kr.co.gcflarchive.student.grade.MyScoresScreen
import kr.co.gcflarchive.student.search.PreviewScreen
import kr.co.gcflarchive.student.survey.SurveyBuilderScreen
import kr.co.gcflarchive.student.survey.SurveyListScreen
import kr.co.gcflarchive.student.survey.SurveyResultsScreen
import kr.co.gcflarchive.student.survey.SurveyScreen
import kr.co.gcflarchive.student.ui.Krds
import kr.co.gcflarchive.student.ui.KrdsTheme

/** Site links the platform hands to the running app (iOS: onOpenURL / universal links). */
object IncomingLinks {
    internal val flow = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)

    fun open(url: String) {
        flow.tryEmit(url)
    }
}

/** Shared process-wide services (one cookie jar / HTTP client for the whole app). */
val sharedServices: Services by lazy { Services() }

/**
 * 학생용 앱 root, identical on iPhone, iPad and Android: bottom tabs plus screens pushed
 * over them. Every stacked screen stays composed so going back keeps its state.
 */
@Composable
fun StudentApp(services: Services = sharedServices, startLink: String? = null) {
    // Images (incl. protected PDF pages) go through the app's HTTP client, so they carry the session.
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx).components { add(KtorNetworkFetcherFactory(services.site.http)) }.crossfade(true).build()
    }
    KrdsTheme {
        val scope = rememberCoroutineScope()
        val app = remember { AppState(services, scope) }
        CompositionLocalProvider(LocalApp provides app) {
            LaunchedEffect(Unit) {
                app.refreshLogin()
                MealAlarms.reschedule(services)
                NoticeAlerts.check(services)
                kr.co.gcflarchive.student.school.SchoolAlerts.check(services)
            }
            // 새 공지 알림: also check whenever the app comes back to the front.
            AppForegroundEffect(onForeground = {
                scope.launch {
                    NoticeAlerts.check(services)
                    kr.co.gcflarchive.student.school.SchoolAlerts.check(services)
                }
            }, onBackground = {})
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            LaunchedEffect(startLink) { startLink?.let { link -> app.openLink(link) { uri.openUri(it) } } }
            // Links handed over later by the platform (universal links, 공유 → 앱).
            LaunchedEffect(Unit) {
                IncomingLinks.flow.collect { link -> app.openLink(link) { uri.openUri(it) } }
            }
            PlatformBackHandler(enabled = app.stack.isNotEmpty()) { app.pop() }

            Box(Modifier.fillMaxSize().background(Krds.colors.bg)) {
                MainTabs(app)
                app.stack.forEachIndexed { i, route ->
                    key(route, i) {
                        Box(Modifier.fillMaxSize().background(Krds.colors.bg)) { RouteScreen(route) }
                    }
                }
                AppLockGate(services.prefs)
            }
        }
    }
}

@Composable
private fun MainTabs(app: AppState) {
    Scaffold(
        containerColor = Krds.colors.bg,
        snackbarHost = { SnackbarHost(app.snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Krds.colors.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = app.tab == t,
                        onClick = { app.tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.HOME -> Icons.Filled.Home
                                    Tab.SEARCH -> Icons.Filled.Search
                                    Tab.MEAL -> Icons.Filled.Restaurant
                                    Tab.DRIVE -> Icons.Filled.CloudUpload
                                    Tab.MORE -> Icons.Filled.MoreHoriz
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Krds.colors.primaryText,
                            selectedTextColor = Krds.colors.primaryText,
                            indicatorColor = Krds.colors.primaryTint,
                            unselectedIconColor = Krds.colors.textSecondary,
                            unselectedTextColor = Krds.colors.textSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (app.tab) {
                Tab.HOME -> HomeScreen()
                Tab.SEARCH -> SearchScreen()
                Tab.MEAL -> MealScreen()
                Tab.DRIVE -> DriveScreen()
                Tab.MORE -> MoreScreen()
            }
        }
    }
}

@Composable
internal fun RouteScreen(route: Route) {
    when (route) {
        Route.Login -> LoginScreen()
        is Route.PostDetail -> PostDetailScreen(route.board, route.idx)
        Route.PastExams -> PastExamsScreen()
        Route.Documents -> DocumentsScreen()
        Route.Photos -> PhotosScreen()
        Route.Videos -> VideosScreen()
        is Route.Viewer -> ViewerScreen(route.filename, route.title, route.includeAnswers)
        Route.Grade -> GradeScreen()
        is Route.Map -> MapScreen(route.placeId, route.placeName)
        Route.Notices -> NoticeListScreen()
        is Route.NoticeDetail -> NoticeDetailScreen(route.id)
        is Route.Upload -> UploadScreen(route.files)
        Route.Settings -> SettingsScreen()
        Route.MyScores -> MyScoresScreen()
        Route.Community -> CommunityScreen()
        Route.Surveys -> SurveyListScreen()
        is Route.Survey -> SurveyScreen(route.id)
        is Route.SurveyBuild -> SurveyBuilderScreen(route.id)
        is Route.SurveyResults -> SurveyResultsScreen(route.id)
        is Route.CommunityPost -> CommunityPostScreen(route.id)
        is Route.CommunityWrite -> CommunityWriteScreen(route)
        is Route.Preview -> PreviewScreen(route.api, route.kind, route.filename, route.downloadUrl)
        is Route.School -> kr.co.gcflarchive.student.school.SchoolScreen(route.tab)
        is Route.SchoolMessage -> kr.co.gcflarchive.student.school.SchoolMessageScreen(route.id)
        is Route.SchoolThread -> kr.co.gcflarchive.student.school.SchoolThreadScreen(route.id)
        is Route.SchoolVisit -> kr.co.gcflarchive.student.school.SchoolVisitScreen(route.id)
        is Route.SchoolAsk -> kr.co.gcflarchive.student.school.SchoolAskScreen(route.teacherId)
        Route.SchoolCompose -> kr.co.gcflarchive.student.school.SchoolComposeScreen()
        is Route.SchoolGroup -> kr.co.gcflarchive.student.school.SchoolGroupScreen(route.id)
    }
}
