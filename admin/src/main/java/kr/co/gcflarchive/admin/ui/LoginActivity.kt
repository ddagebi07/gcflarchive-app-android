package kr.co.gcflarchive.admin.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.co.gcflarchive.admin.AdminApp
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminApi
import kr.co.gcflarchive.admin.core.AdminPrefs
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.core.Auth
import kr.co.gcflarchive.admin.databinding.ActivityLoginBinding
import kr.co.gcflarchive.admin.ui.kit.Dialogs
import kr.co.gcflarchive.admin.ui.kit.Insets

/** 관리자 인증: 마스터 키 또는 관리자 학번(학번 + 비밀번호). */
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (AdminSession.get(this).isSignedIn) {
            openMain()
            return
        }
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.apply(this, binding.root)
        val prefs = AdminPrefs(this)
        binding.server.setText(prefs.serverUrl)

        binding.modeGroup.check(R.id.modeHakbun)
        binding.modeGroup.addOnButtonCheckedListener { _, id, checked -> if (checked) render(id == R.id.modeMaster) }
        render(master = false)

        binding.btnLogin.setOnClickListener { submit() }
    }

    private fun render(master: Boolean) {
        binding.masterLayout.isVisible = master
        binding.hakbunLayout.isVisible = !master
        binding.passwordLayout.isVisible = !master
    }

    private fun submit() {
        val server = binding.server.text?.toString().orEmpty().trim().trimEnd('/')
        if (!server.startsWith("https://")) {
            binding.error.isVisible = true
            binding.error.text = getString(R.string.login_https_only)
            return
        }
        val prefs = AdminPrefs(this)
        if (server != prefs.serverUrl) {
            AdminApi.get(this).clearAll()
            prefs.serverUrl = server
            AdminApi.reset()
        }
        val master = binding.modeGroup.checkedButtonId == R.id.modeMaster
        binding.btnLogin.isEnabled = false
        binding.error.isVisible = false
        lifecycleScope.launch {
            runCatching {
                if (master) {
                    Auth.loginWithMasterKey(this@LoginActivity, binding.masterKey.text?.toString().orEmpty().trim())
                } else {
                    Auth.loginWithHakbun(
                        this@LoginActivity,
                        binding.hakbun.text?.toString().orEmpty().trim(),
                        binding.password.text?.toString().orEmpty(),
                    )
                }
            }.onSuccess {
                (application as AdminApp).lock.onUnlocked(System.currentTimeMillis())
                openMain()
            }.onFailure { e ->
                binding.btnLogin.isEnabled = true
                binding.error.isVisible = true
                binding.error.text = Dialogs.messageOf(this@LoginActivity, e)
            }
        }
    }

    private fun openMain() {
        // Forward deep links (widget / shortcuts use gcfladmin://<route>).
        val next = Intent(this, MainActivity::class.java).setData(intent?.data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent?.extras?.let { next.putExtras(it) }
        startActivity(next)
        finish()
    }
}
