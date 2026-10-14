package kr.co.gcflarchive.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import kr.co.gcflarchive.app.data.AppPrefs
import kr.co.gcflarchive.app.databinding.ActivityLockBinding
import kr.co.gcflarchive.app.util.AppLock
import kr.co.gcflarchive.app.util.Biometric

/** 앱 잠금 화면. 지문·얼굴 또는 기기 잠금으로 풀어요. */
class LockActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps the app's content out of the recents preview while locked.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnUnlock.setOnClickListener { prompt() }
        // Never reveal the screen underneath: back sends the app to the background.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        if (savedInstanceState == null) prompt()
    }

    private fun prompt() {
        if (!Biometric.isAvailable(this)) {
            // The device lock was removed after the app lock was turned on: nothing to check against.
            AppPrefs(this).appLockEnabled = false
            unlocked()
            return
        }
        Biometric.authenticate(this, getString(R.string.lock_prompt_title), null, onError = { binding.message.text = it }) {
            unlocked()
        }
    }

    private fun unlocked() {
        AppLock.markUnlocked()
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
