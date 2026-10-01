package kr.co.gcflarchive.admin.core

import android.content.Context
import org.json.JSONObject

/** Sign-in flows for both admin modes. */
object Auth {
    /** Validates a master key against an endpoint every admin scope can't reach without it. */
    suspend fun loginWithMasterKey(context: Context, key: String) {
        val session = AdminSession.get(context)
        session.signInMaster(key)
        try {
            AdminApi.get(context).get("/api/admins", cache = false)
        } catch (e: Exception) {
            session.signOut()
            throw if (e is AuthException) AuthException("마스터 키가 올바르지 않습니다.") else e
        }
    }

    /** Student login, then confirms the hakbun is in the admin list. */
    suspend fun loginWithHakbun(context: Context, hakbun: String, password: String) {
        val api = AdminApi.get(context)
        AdminSession.get(context).signOut()
        api.postJson(
            "/api/verify-login",
            JSONObject().put("hakbun", hakbun).put("password", password).put("rememberMe", true).put("source", "admin_app"),
        )
        val scopes = fetchScopes(context) ?: run {
            logoutServer(context)
            throw AuthException("관리자 권한이 없는 계정입니다.")
        }
        AdminSession.get(context).signInHakbun(hakbun, scopes)
    }

    /** null when the cookie session is not an admin. */
    suspend fun fetchScopes(context: Context): List<String>? {
        val json = AdminApi.get(context).get("/api/admin-status", cache = false).json()
        if (!json.optBoolean("isAdmin")) return null
        val arr = json.optJSONArray("permissions") ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    suspend fun logoutServer(context: Context) {
        runCatching { AdminApi.get(context).postJson("/api/verify-logout", JSONObject()) }
    }

    suspend fun signOut(context: Context) {
        if (AdminSession.get(context).mode == AdminSession.Mode.HAKBUN) logoutServer(context)
        AdminSession.get(context).signOut()
        AdminApi.get(context).clearAll()
        AdminPrefs(context).clearSnapshots()
    }
}
