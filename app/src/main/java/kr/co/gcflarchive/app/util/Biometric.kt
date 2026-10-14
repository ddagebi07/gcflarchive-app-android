package kr.co.gcflarchive.app.util

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kr.co.gcflarchive.app.R

/** 지문(생체) 인증 후에만 민감한 값을 보여 줄 때 씁니다. 지문이 없는 기기는 화면 잠금으로 대신합니다. */
object Biometric {
    // BIOMETRIC_STRONG | DEVICE_CREDENTIAL is only supported from API 30.
    private val authenticators =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** 지문·얼굴 또는 화면 잠금(PIN·패턴·비밀번호)이 설정돼 있는지. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        onError: ((CharSequence) -> Unit)? = null,
        onSuccess: () -> Unit,
    ) {
        if (!isAvailable(activity)) {
            Toast.makeText(activity, activity.getString(R.string.biometric_unavailable), Toast.LENGTH_LONG).show()
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let(::setSubtitle) }
            .setAllowedAuthenticators(authenticators)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED
                when {
                    onError != null -> onError(if (cancelled) activity.getString(R.string.lock_cancelled) else errString)
                    !cancelled -> Toast.makeText(activity, errString, Toast.LENGTH_SHORT).show()
                }
            }
        }).authenticate(info)
    }
}
