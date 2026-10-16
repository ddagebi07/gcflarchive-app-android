package kr.co.gcflarchive.student.school

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import kr.co.gcflarchive.student.ui.ThemeMode
import kr.co.gcflarchive.core.Services
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.prefs.AppPrefs
import kr.co.gcflarchive.student.platform.AndroidPlatform

/**
 * 학교생활 in the Android user app: the shared Compose screens (same as iPhone/iPad) on the
 * app's own login (WebkitCookies). Opened from 홈 · 더보기 and from 학교생활 notifications.
 */
class SchoolActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 설정 > 화면 모드: status bar icons match the chosen light/dark (system = follow the device).
        when (ThemeMode.mode) {
            AppPrefs.THEME_LIGHT -> enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
            AppPrefs.THEME_DARK -> enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
            else -> enableEdgeToEdge()
        }
        AndroidPlatform.context = applicationContext
        val services = services(applicationContext)
        setContent {
            SchoolHost(
                services = services,
                startTab = intent.getStringExtra(EXTRA_TAB),
                target = intent.getStringExtra(EXTRA_TARGET),
                onExit = { finish() },
            )
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        /** A push target such as "visit:12" (UserNotification.target on the server). */
        const val EXTRA_TARGET = "target"

        @Volatile private var shared: Services? = null

        /** One client for the process, on the app's WebKit cookie store. */
        fun services(context: Context): Services = shared ?: synchronized(this) {
            shared ?: Services(site = SiteClient(cookies = WebkitCookies()), prefs = AppPrefs()).also {
                AndroidPlatform.context = context.applicationContext
                shared = it
            }
        }

        fun intent(context: Context, tab: String? = null, target: String? = null): Intent =
            Intent(context, SchoolActivity::class.java).apply {
                tab?.let { putExtra(EXTRA_TAB, it) }
                target?.let { putExtra(EXTRA_TARGET, it) }
            }
    }
}
