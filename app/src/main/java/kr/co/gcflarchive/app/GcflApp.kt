package kr.co.gcflarchive.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kr.co.gcflarchive.app.meal.MealNotifier
import kr.co.gcflarchive.app.notice.NoticeNotifier
import kr.co.gcflarchive.app.school.SchoolNotifier
import kr.co.gcflarchive.app.school.SchoolPushService
import kr.co.gcflarchive.app.util.AppLock

class GcflApp : Application() {
    companion object {
        /** 설정 > 화면 모드: "system"(기본) / "light" / "dark" for every AppCompat screen (recreates open ones). */
        fun applyThemeMode(mode: String) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    kr.co.gcflarchive.core.prefs.AppPrefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    kr.co.gcflarchive.core.prefs.AppPrefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                },
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        applyThemeMode(kr.co.gcflarchive.core.prefs.AppPrefs().themeMode)
        AppLock.install(this)
        MealNotifier.createChannel(this)
        MealNotifier.schedule(this, replace = false)
        NoticeNotifier.createChannel(this)
        NoticeNotifier.schedule(this, replace = false)
        SchoolNotifier.createChannels(this)
        SchoolNotifier.schedule(this)
        SchoolPushService.refreshToken(this)
        kr.co.gcflarchive.app.widget.MealWidget.refresh(this)
    }
}
