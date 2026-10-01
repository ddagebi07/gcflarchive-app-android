package kr.co.gcflarchive.admin.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.co.gcflarchive.admin.AdminApp
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.core.Auth
import kr.co.gcflarchive.admin.databinding.ActivityLockBinding
import kr.co.gcflarchive.admin.ui.kit.Insets

/** 생체 인증(지문·얼굴) 또는 기기 잠금으로 재잠금 해제. */
class LockActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLockBinding

    // BIOMETRIC_STRONG | DEVICE_CREDENTIAL is only supported from API 30.
    private val authenticators =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.apply(this, binding.root)
        binding.who.text = AdminSession.get(this).displayName
        binding.btnUnlock.setOnClickListener { prompt() }
        binding.btnSignOut.setOnClickListener {
            lifecycleScope.launch {
                Auth.signOut(this@LockActivity)
                startActivity(Intent(this@LockActivity, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        }
        // Never reveal the screen underneath: back sends the app to the background.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        prompt()
    }

    private fun prompt() {
        val can = BiometricManager.from(this).canAuthenticate(authenticators)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock on the device: fall back to signing in again.
            binding.message.text = getString(R.string.lock_no_device_lock)
            binding.btnUnlock.isVisible = false
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_title))
            .setSubtitle(AdminSession.get(this).displayName)
            .setAllowedAuthenticators(authenticators)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                (application as AdminApp).lock.onUnlocked(System.currentTimeMillis())
                finish()
                overridePendingTransition(0, 0)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                binding.message.text = errString
            }
        }).authenticate(info)
    }
}
