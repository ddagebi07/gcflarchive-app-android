package kr.co.gcflarchive.app.data

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.app.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/** The site answered 401: the user has to log in (on the website) first. */
class LoginRequiredException(message: String = "로그인이 필요합니다.") : IOException(message)

class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Calls the website's JSON APIs with the shared session cookie (android.webkit.CookieManager
 * is used purely as the app's persistent cookie store), so one native login
 * authenticates every request (and refreshed cookies flow back).
 */
object SiteApi {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun request(path: String): Request.Builder {
        val url = if (path.startsWith("http")) path else Config.url(path)
        val builder = Request.Builder().url(url)
        // The session cookie only ever goes to our own host, never to thumbnail CDNs.
        if (Config.isOwnHost(android.net.Uri.parse(url))) {
            CookieManager.getInstance().getCookie(Config.BASE_URL)?.let { builder.header("Cookie", it) }
        }
        return builder
    }

    suspend fun get(path: String): String = execute(request(path).build())

    suspend fun postJson(path: String, body: JSONObject): String =
        execute(request(path).post(body.toString().toRequestBody(JSON)).build())

    /** Status + body without throwing on HTTP errors (for APIs whose error bodies carry flags). */
    data class Response(val code: Int, val body: String) {
        val isSuccessful: Boolean get() = code in 200..299
        val json: JSONObject get() = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
        val error: String? get() = json.optString("error").ifBlank { null }
    }

    suspend fun exchange(path: String, method: String = "GET", body: JSONObject? = null): Response = withContext(Dispatchers.IO) {
        val builder = request(path)
        when {
            body != null -> builder.method(method, body.toString().toRequestBody(JSON))
            method != "GET" -> builder.method(method, "{}".toRequestBody(JSON))
        }
        Http.client.newCall(builder.build()).execute().use { res ->
            syncCookies(res)
            Response(res.code, res.body?.string().orEmpty())
        }
    }

    /** Raw bytes (e.g. viewer page images); 401 maps to [LoginRequiredException]. */
    suspend fun bytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        Http.client.newCall(request(path).build()).execute().use { res ->
            syncCookies(res)
            if (res.code == 401) throw LoginRequiredException()
            if (!res.isSuccessful) throw ApiException(res.code, "HTTP ${res.code}")
            res.body?.bytes() ?: ByteArray(0)
        }
    }

    private suspend fun execute(request: okhttp3.Request): String = withContext(Dispatchers.IO) {
        Http.client.newCall(request).execute().use { res ->
            syncCookies(res)
            val text = res.body?.string().orEmpty()
            // Keep the server's wording (e.g. "학번 또는 비밀번호가 일치하지 않습니다.").
            val msg = runCatching { JSONObject(text).optString("error") }.getOrNull()?.ifBlank { null }
            if (res.code == 401) throw if (msg != null) LoginRequiredException(msg) else LoginRequiredException()
            if (!res.isSuccessful) throw ApiException(res.code, msg ?: "HTTP ${res.code}")
            text
        }
    }

    private fun syncCookies(res: Response) {
        if (!Config.isOwnHost(android.net.Uri.parse(res.request.url.toString()))) return
        val headers = res.headers("Set-Cookie")
        if (headers.isEmpty()) return
        val cm = CookieManager.getInstance()
        headers.forEach { cm.setCookie(Config.BASE_URL, it) }
        cm.flush()
    }
}
