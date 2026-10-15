package kr.co.gcflarchive.app.data

import org.json.JSONObject

/** Website login state, read with the shared session cookie. */
sealed interface LoginState {
    /** [displayName]: a teacher's 성명 (blank for students). */
    data class LoggedIn(val userId: String, val userType: String, val displayName: String = "") : LoginState
    data object LoggedOut : LoginState
    /** Offline or server error: don't nag the user to log in. */
    data object Unknown : LoginState
}

object SiteSession {
    suspend fun state(): LoginState = runCatching {
        val json = JSONObject(SiteApi.get("/api/verify-status"))
        if (json.optBoolean("verified") && json.optString("userId").isNotBlank()) {
            LoginState.LoggedIn(json.optString("userId"), json.optString("userType"), json.optString("displayName"))
        } else {
            LoginState.LoggedOut
        }
    }.getOrDefault(LoginState.Unknown)

    /** The logged-in 학번 (or teacher ID), or null when logged out or offline. */
    suspend fun currentUserId(): String? = (state() as? LoginState.LoggedIn)?.userId
}
