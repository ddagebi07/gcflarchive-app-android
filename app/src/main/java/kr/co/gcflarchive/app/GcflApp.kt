package kr.co.gcflarchive.app

import android.app.Application
import kr.co.gcflarchive.app.meal.MealNotifier
import kr.co.gcflarchive.app.notice.NoticeNotifier
import kr.co.gcflarchive.app.school.SchoolNotifier
import kr.co.gcflarchive.app.school.SchoolPushService
import kr.co.gcflarchive.app.util.AppLock

class GcflApp : Application() {
    override fun onCreate() {
        super.onCreate()
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
