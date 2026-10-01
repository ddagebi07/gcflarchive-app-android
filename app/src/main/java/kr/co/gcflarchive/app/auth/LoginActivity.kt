package kr.co.gcflarchive.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.data.ApiException
import kr.co.gcflarchive.app.data.AuthApi
import kr.co.gcflarchive.app.data.LoginRequiredException
import kr.co.gcflarchive.app.databinding.ActivityLoginBinding

/**
 * Native login screen (replaces the /verify WebView). Talks to the same JSON APIs as
 * verify.html, including the first-login 개인정보 동의 step. Finishes with RESULT_OK once
 * the session is fully usable; the cookie is shared with every WebView in the app.
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private var mode = AuthApi.Mode.STUDENT
    private var loggedInId = ""
    private var consentMode: AuthApi.Mode? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { cancel() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = if (consentMode != null) backToLogin() else cancel()
        })

        binding.modeGroup.check(R.id.modeStudent)
        binding.modeGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) setMode(if (id == R.id.modeTeacher) AuthApi.Mode.TEACHER else AuthApi.Mode.STUDENT)
        }
        binding.password.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { submitLogin(); true } else false
        }
        binding.btnLogin.setOnClickListener { submitLogin() }

        binding.consentRequired.setOnCheckedChangeListener { _, checked -> binding.btnConsent.isEnabled = checked && !busy }
        binding.btnConsent.setOnClickListener { submitConsent() }
        binding.btnConsentBack.setOnClickListener { backToLogin() }
    }

    private fun setMode(newMode: AuthApi.Mode) {
        mode = newMode
        val teacher = newMode == AuthApi.Mode.TEACHER
        binding.idLayout.hint = getString(if (teacher) R.string.auth_teacher_id else R.string.auth_hakbun)
        binding.loginId.inputType = if (teacher) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        else InputType.TYPE_CLASS_NUMBER
        binding.loginId.filters = if (teacher) emptyArray<InputFilter>() else arrayOf<InputFilter>(InputFilter.LengthFilter(5))
        binding.loginId.text = null
        binding.loginError.isVisible = false
    }

    private fun submitLogin() {
        if (busy) return
        val id = binding.loginId.text?.toString()?.trim().orEmpty()
        val password = binding.password.text?.toString().orEmpty()
        val invalid = when {
            mode == AuthApi.Mode.STUDENT && !(id.length == 5 && id.all(Char::isDigit)) -> R.string.auth_invalid_hakbun
            mode == AuthApi.Mode.TEACHER && id.isEmpty() -> R.string.auth_invalid_teacher
            password.isBlank() -> R.string.auth_password_required
            else -> null
        }
        if (invalid != null) return showError(binding.loginError, getString(invalid))

        setBusy(true)
        binding.loginError.isVisible = false
        lifecycleScope.launch {
            val result = runCatching { AuthApi.login(mode, id, password, binding.rememberMe.isChecked) }
            setBusy(false)
            result.onSuccess { res ->
                binding.password.text = null
                loggedInId = id
                if (res.consentRequired) showConsent(res) else finishLoggedIn(res.teacherName)
            }.onFailure { e -> showError(binding.loginError, messageFor(e)) }
        }
    }

    private fun showConsent(res: AuthApi.LoginResult) {
        val teacher = mode == AuthApi.Mode.TEACHER || res.userType == "teacher"
        consentMode = if (teacher) AuthApi.Mode.TEACHER else AuthApi.Mode.STUDENT
        binding.loginPanel.isVisible = false
        binding.consentPanel.isVisible = true
        binding.heading.setText(R.string.auth_consent_title)
        binding.subheading.setText(R.string.auth_consent_desc)
        binding.consentTerms.setText(if (teacher) R.string.auth_consent_terms_teacher else R.string.auth_consent_terms_student)
        binding.consentRequired.setText(if (teacher) R.string.auth_consent_required_teacher else R.string.auth_consent_required)
        binding.consentRequired.isChecked = false
        binding.consentScore.isVisible = !teacher
        binding.consentScore.isChecked = false
        binding.teacherNameLayout.isVisible = teacher
        binding.teacherEmailLayout.isVisible = teacher
        binding.teacherName.setText(res.teacherName)
        binding.teacherEmail.setText(res.teacherEmail)
        binding.consentError.isVisible = false
        binding.btnConsent.isEnabled = false
    }

    private fun submitConsent() {
        val consent = consentMode ?: return
        if (busy) return
        if (!binding.consentRequired.isChecked) return showError(binding.consentError, getString(R.string.auth_consent_declined))
        val name = binding.teacherName.text?.toString()?.trim().orEmpty()
        val email = binding.teacherEmail.text?.toString()?.trim().orEmpty()
        if (consent == AuthApi.Mode.TEACHER && (name.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches())) {
            return showError(binding.consentError, getString(R.string.auth_teacher_fields))
        }

        setBusy(true)
        binding.consentError.isVisible = false
        lifecycleScope.launch {
            val result = runCatching {
                if (consent == AuthApi.Mode.TEACHER) AuthApi.consentTeacher(name, email)
                else AuthApi.consentStudent(binding.consentScore.isChecked)
            }
            setBusy(false)
            result.onSuccess { finishLoggedIn(name) }
                .onFailure { e -> showError(binding.consentError, messageFor(e)) }
        }
    }

    /** Declining consent must not leave a half-verified session behind. */
    private fun backToLogin() {
        if (busy) return
        consentMode = null
        logoutDetached()
        binding.consentPanel.isVisible = false
        binding.loginPanel.isVisible = true
        binding.heading.setText(R.string.auth_title)
        binding.subheading.setText(R.string.auth_desc)
    }

    private fun cancel() {
        if (consentMode != null) logoutDetached()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    /** Outlives this screen, so closing right after still clears the session. */
    private fun logoutDetached() {
        CoroutineScope(Dispatchers.IO).launch { AuthApi.logout() }
    }

    private fun finishLoggedIn(displayName: String) {
        val who = displayName.ifBlank { loggedInId }
        Toast.makeText(this, getString(R.string.auth_success, who), Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.btnLogin.isEnabled = !value
        binding.btnLogin.text = getString(if (value) R.string.auth_working else R.string.auth_submit)
        binding.btnConsent.isEnabled = !value && binding.consentRequired.isChecked
        binding.btnConsentBack.isEnabled = !value
        binding.modeGroup.isEnabled = !value
    }

    private fun showError(view: android.widget.TextView, message: String) {
        view.text = message
        view.isVisible = true
    }

    private fun messageFor(e: Throwable): String = when (e) {
        is CancellationException -> throw e
        is LoginRequiredException, is ApiException -> e.message ?: getString(R.string.auth_network)
        else -> getString(R.string.auth_network)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, LoginActivity::class.java)
    }
}
