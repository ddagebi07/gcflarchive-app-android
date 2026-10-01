package kr.co.gcflarchive.admin.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import kr.co.gcflarchive.admin.AdminApp
import kr.co.gcflarchive.admin.core.AdminPrefs
import kr.co.gcflarchive.admin.core.AdminSession

/**
 * Every signed-in screen: blocks screenshots/recents previews (FLAG_SECURE, on by
 * default), sends signed-out users to login and locked sessions to the biometric gate,
 * and re-locks after 5 minutes without interaction.
 */
abstract class BaseActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val idleCheck = object : Runnable {
        override fun run() {
            if (app.lock.checkIdle(System.currentTimeMillis())) gate() else handler.postDelayed(this, 15_000)
        }
    }

    protected val app: AdminApp get() = application as AdminApp

    override fun onResume() {
        super.onResume()
        if (AdminPrefs(this).secureScreens) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (!gate()) handler.postDelayed(idleCheck, 15_000)
    }

    override fun onPause() {
        handler.removeCallbacks(idleCheck)
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        app.lock.onInteraction(System.currentTimeMillis())
    }

    /** Returns true when the user was redirected away. */
    private fun gate(): Boolean {
        if (!AdminSession.get(this).isSignedIn) {
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return true
        }
        if (app.lock.locked) {
            startActivity(Intent(this, LockActivity::class.java))
            overridePendingTransition(0, 0)
            return true
        }
        return false
    }
}
