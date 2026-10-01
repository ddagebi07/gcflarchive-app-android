package kr.co.gcflarchive.app

import android.app.Application
import kr.co.gcflarchive.app.meal.MealNotifier

class GcflApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MealNotifier.createChannel(this)
        MealNotifier.schedule(this, replace = false)
    }
}
