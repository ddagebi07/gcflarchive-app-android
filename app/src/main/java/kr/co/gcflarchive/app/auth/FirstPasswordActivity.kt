package kr.co.gcflarchive.app.auth

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.data.ApiException
import kr.co.gcflarchive.app.data.AuthApi
import kr.co.gcflarchive.app.databinding.ActivityFirstPasswordBinding
import kr.co.gcflarchive.app.util.Links

/**
 * 비밀번호를 잊은 학생: 학번 + 이름으로 STUCARE 초기·리셋 비밀번호를 확인해요
 * (STUCARE의 myfirst.php와 같은 일, 서버 /api/first-password를 거쳐요).
 * "이 비밀번호로 로그인하기" returns RESULT_OK with the 학번 and password for LoginActivity to fill in.
 */
class FirstPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFirstPasswordBinding
    private var busy = false
    private var found: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A password is on screen: keep it out of screenshots and the recents preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityFirstPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        intent.getStringExtra(EXTRA_HAKBUN)?.let { binding.hakbun.setText(it) }
        binding.name.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        binding.btnSubmit.setOnClickListener { submit() }
        binding.btnCopy.setOnClickListener { copy() }
        binding.btnUse.setOnClickListener {
            val (hakbun, password) = found ?: return@setOnClickListener
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_HAKBUN, hakbun).putExtra(EXTRA_PASSWORD, password))
            finish()
        }
        binding.btnChange.setOnClickListener { Links.openExternal(this, Uri.parse(CHANGE_URL)) }
    }

    private fun submit() {
        if (busy) return
        val hakbun = binding.hakbun.text?.toString()?.trim().orEmpty()
        val name = binding.name.text?.toString()?.trim().orEmpty()
        when {
            !(hakbun.length == 5 && hakbun.all(Char::isDigit)) -> return showError(getString(R.string.auth_invalid_hakbun))
            name.isEmpty() -> return showError(getString(R.string.fp_invalid_name))
        }
        setBusy(true)
        binding.error.isVisible = false
        binding.resultPanel.isVisible = false
        found = null
        lifecycleScope.launch {
            val result = runCatching { AuthApi.firstPassword(hakbun, name) }
            setBusy(false)
            result.onSuccess { password ->
                found = hakbun to password
                binding.resultTitle.text = getString(R.string.fp_result_title, name)
                binding.password.text = password
                binding.resultPanel.isVisible = true
            }.onFailure { e ->
                if (e is CancellationException) throw e
                showError(if (e is ApiException) e.message.orEmpty() else getString(R.string.auth_network))
            }
        }
    }

    private fun copy() {
        val password = found?.second ?: return
        val clip = ClipData.newPlainText(getString(R.string.fp_title), password)
        // Hides the value from the clipboard preview on Android 13+.
        val extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)  // = ClipDescription.EXTRA_IS_SENSITIVE (API 33)
        }
        clip.description.extras = extras
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.fp_copied, Toast.LENGTH_SHORT).show()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.btnSubmit.isEnabled = !value
        binding.btnSubmit.text = getString(if (value) R.string.auth_working else R.string.fp_submit)
    }

    private fun showError(message: String) {
        binding.error.text = message
        binding.error.isVisible = true
    }

    companion object {
        const val EXTRA_HAKBUN = "hakbun"
        const val EXTRA_PASSWORD = "password"
        private const val CHANGE_URL = "https://www.stucare.net/stucare/gcfl/course/passchange.php"

        fun intent(context: Context, hakbun: String? = null): Intent =
            Intent(context, FirstPasswordActivity::class.java).putExtra(EXTRA_HAKBUN, hakbun)
    }
}
