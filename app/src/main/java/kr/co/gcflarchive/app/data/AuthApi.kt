package kr.co.gcflarchive.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Native version of verify.html's login + consent flow (routes/auth.py):
 *  - 학생 → /api/verify-login, 특수 계정(60000~99999) → /api/login-custom-account,
 *    교사 → /api/teacher-login
 *  - first login needs 개인정보 동의 → /api/consent
 * The Flask session cookie lands in the WebView CookieManager via [SiteApi], so the
 * web screens and native features share one login.
 */
object AuthApi {
    enum class Mode { STUDENT, TEACHER }

    data class LoginResult(val consentRequired: Boolean, val userType: String, val teacherName: String, val teacherEmail: String)

    fun isCustomAccount(id: String): Boolean = id.length == 5 && id.toIntOrNull()?.let { it in 60000..99999 } == true

    fun endpointFor(mode: Mode, id: String): String = when {
        mode == Mode.TEACHER -> "/api/teacher-login"
        isCustomAccount(id) -> "/api/login-custom-account"
        else -> "/api/verify-login"
    }

    /** Throws ApiException with the server's message (e.g. 로그인 차단됨: 사유). */
    suspend fun login(mode: Mode, id: String, password: String, rememberMe: Boolean): LoginResult {
        val body = JSONObject()
            .put("hakbun", id)
            .put("teacherId", id)
            .put("password", password)
            .put("student_password", password)
            .put("next", "/")
            .put("source", "android_app")
            .put("loginMode", if (mode == Mode.TEACHER) "teacher" else "student")
            .put("rememberMe", rememberMe)
        val json = JSONObject(SiteApi.postJson(endpointFor(mode, id), body))
        return LoginResult(
            consentRequired = json.optBoolean("consentRequired"),
            userType = json.optString("userType").ifBlank { json.optString("accountType").ifBlank { if (mode == Mode.TEACHER) "teacher" else "student" } },
            teacherName = json.optString("teacherName"),
            teacherEmail = json.optString("teacherEmail"),
        )
    }

    suspend fun consentStudent(scoreConsent: Boolean) {
        val scopes = JSONArray().put("hakbun").apply { if (scoreConsent) put("test_score") }
        SiteApi.postJson(
            "/api/consent",
            JSONObject().put("accepted", true).put("next", "/").put("scopes", scopes)
                .put("scoreConsentGranted", scoreConsent).put("accountType", "student").put("userType", "student"),
        )
    }

    suspend fun consentTeacher(name: String, email: String) {
        SiteApi.postJson(
            "/api/consent",
            JSONObject().put("accepted", true).put("next", "/")
                .put("teacherName", name).put("teacherEmail", email).put("name", name).put("email", email)
                .put("accountType", "teacher").put("userType", "teacher").put("teacherConsentGranted", true),
        )
    }

    suspend fun logout() {
        runCatching { SiteApi.postJson("/api/verify-logout", JSONObject()) }
    }
}
