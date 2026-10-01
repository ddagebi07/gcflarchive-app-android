package kr.co.gcflarchive.admin

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kr.co.gcflarchive.admin.core.AdminPrefs
import kr.co.gcflarchive.admin.core.AppLock
import kr.co.gcflarchive.admin.notify.AdminNotifier
import kr.co.gcflarchive.admin.notify.PollWorker

class AdminApp : Application() {
    val lock = AppLock()

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AdminPrefs(this).nightMode)
        AdminNotifier.createChannels(this)
        PollWorker.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = lock.onForeground(System.currentTimeMillis())
            override fun onStop(owner: LifecycleOwner) = lock.onBackground(System.currentTimeMillis())
        })
    }
}
