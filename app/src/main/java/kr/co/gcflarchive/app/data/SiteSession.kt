package kr.co.gcflarchive.app.data

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.app.Config
import okhttp3.Request
import org.json.JSONObject

/** Reads the website login state using the WebView's session cookie. */
object SiteSession {
    /** The logged-in 학번 (GET /api/verify-status → userId), or null when logged out or offline. */
    suspend fun currentUserId(): String? = withContext(Dispatchers.IO) {
        val cookie = CookieManager.getInstance().getCookie(Config.BASE_URL)
        if (cookie.isNullOrBlank()) return@withContext null
        val request = Request.Builder()
            .url(Config.url("/api/verify-status"))
            .header("Cookie", cookie)
            .build()
        runCatching {
            Http.client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val json = JSONObject(res.body?.string().orEmpty())
                if (json.optBoolean("verified")) json.optString("userId").ifBlank { null } else null
            }
        }.getOrNull()
    }
}
