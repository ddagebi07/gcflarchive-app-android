package kr.co.gcflarchive.core.auth

import kr.co.gcflarchive.core.net.ApiException
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.jsonOf

/** Website login state, read from /api/verify-status with the shared session cookie. */
sealed interface LoginState {
    data class LoggedIn(val userId: String, val userType: String, val scoreConsent: Boolean, val hakbunConsent: Boolean, val isAdmin: Boolean) : LoginState
    data object LoggedOut : LoginState
    /** Offline / server error: don't nag the user to log in. */
    data object Unknown : LoginState
}

/**
 * Native version of verify.html's login + consent flow (routes/auth.py):
 *  - 학생 → /api/verify-login, 특수 계정(60000~99999) → /api/login-custom-account,
 *    교사 → /api/teacher-login
 *  - first login needs 개인정보 동의 → /api/consent
 */
class AuthApi(private val site: SiteClient) {
    enum class Mode { STUDENT, TEACHER }

    data class LoginResult(val consentRequired: Boolean, val userType: String, val teacherName: String, val teacherEmail: String)

    suspend fun state(): LoginState = runCatching {
        val j = site.exchange("/api/verify-status").also { if (!it.isSuccessful) error("HTTP ${it.code}") }.json
        if (j.optBoolean("verified") && j.optString("userId").isNotBlank()) {
            LoginState.LoggedIn(j.optString("userId"), j.optString("userType"), j.optBoolean("scoreConsentGranted"), j.optBoolean("hakbunConsentGranted"), j.optBoolean("isAdmin"))
        } else {
            LoginState.LoggedOut
        }
    }.getOrDefault(LoginState.Unknown)

    suspend fun login(mode: Mode, id: String, password: String, rememberMe: Boolean): LoginResult {
        val j = site.postForJson(
            endpointFor(mode, id),
            jsonOf(
                "hakbun" to id, "teacherId" to id, "password" to password, "student_password" to password,
                "next" to "/", "source" to "app", "loginMode" to if (mode == Mode.TEACHER) "teacher" else "student",
                "rememberMe" to rememberMe,
            ),
        )
        return LoginResult(
            consentRequired = j.optBoolean("consentRequired"),
            userType = j.optString("userType").ifBlank { j.optString("accountType").ifBlank { if (mode == Mode.TEACHER) "teacher" else "student" } },
            teacherName = j.optString("teacherName"),
            teacherEmail = j.optString("teacherEmail"),
        )
    }

    suspend fun consentStudent(scoreConsent: Boolean) {
        site.postJson(
            "/api/consent",
            jsonOf(
                "accepted" to true, "next" to "/",
                "scopes" to if (scoreConsent) listOf("hakbun", "test_score") else listOf("hakbun"),
                "scoreConsentGranted" to scoreConsent, "accountType" to "student", "userType" to "student",
            ),
        )
    }

    suspend fun consentTeacher(name: String, email: String) {
        site.postJson(
            "/api/consent",
            jsonOf(
                "accepted" to true, "next" to "/", "teacherName" to name, "teacherEmail" to email, "name" to name, "email" to email,
                "accountType" to "teacher", "userType" to "teacher", "teacherConsentGranted" to true,
            ),
        )
    }

    /**
     * 학생 초기·리셋 비밀번호 (학교 STUCARE의 My First Password): the server asks STUCARE
     * (/api/first-password) and limits how often. Throws [ApiException] with the message to show.
     */
    suspend fun firstPassword(hakbun: String, name: String): String {
        val res = site.exchange("/api/first-password", "POST", jsonOf("hakbun" to hakbun, "name" to name))
        val password = res.json.optString("password")
        if (res.isSuccessful && password.isNotBlank()) return password
        throw ApiException(res.code, res.error ?: "학번과 이름을 다시 확인해주세요.")
    }

    /** Server logout, then forget the cookie locally even if the server is unreachable. */
    suspend fun logout() {
        runCatching { site.postJson("/api/verify-logout") }
        site.cookies.clear()
    }

    companion object {
        fun isCustomAccount(id: String): Boolean = id.length == 5 && id.toIntOrNull()?.let { it in 60000..99999 } == true

        fun endpointFor(mode: Mode, id: String): String = when {
            mode == Mode.TEACHER -> "/api/teacher-login"
            isCustomAccount(id) -> "/api/login-custom-account"
            else -> "/api/verify-login"
        }

        fun isValidHakbun(id: String): Boolean = id.length == 5 && id.all(Char::isDigit)
        fun isValidEmail(email: String): Boolean = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(email.trim())
    }
}
