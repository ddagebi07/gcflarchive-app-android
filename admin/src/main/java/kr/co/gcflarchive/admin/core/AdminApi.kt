package kr.co.gcflarchive.admin.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthException(message: String) : IOException(message)
class ApiError(val code: Int, message: String) : IOException(message)
class OfflineException : IOException("오프라인 상태에서는 변경할 수 없습니다.")

/** A GET result, possibly served from the offline cache. */
data class Fetched(val body: String, val fromCache: Boolean, val cachedAt: Long = 0) {
    fun json(): JSONObject = JSONObject(body)
}

/**
 * HTTP client for the admin APIs. Auth is the master key header (MASTER mode) or the
 * Flask session cookie (HAKBUN mode); both only ever go to the configured server host.
 */
class AdminApi private constructor(private val context: Context) {
    private val prefs = AdminPrefs(context)
    private val store = SecureStore(context)
    private val session = AdminSession.get(context)
    private val cacheDir = File(context.filesDir, "api_cache").apply { mkdirs() }

    private val cookieJar = object : CookieJar {
        private val cookies = mutableMapOf<String, Cookie>()

        init {
            store.get(COOKIE_KEY)?.let { saved ->
                runCatching {
                    val arr = JSONArray(saved)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val url = o.getString("url").toHttpUrl()
                        Cookie.parse(url, o.getString("cookie"))?.let { cookies[it.name] = it }
                    }
                }
            }
        }

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (!isServerHost(url)) return
            cookies.forEach { this.cookies[it.name] = it }
            val arr = JSONArray()
            this.cookies.values.forEach { arr.put(JSONObject().put("url", url.toString()).put("cookie", it.toString())) }
            store.put(COOKIE_KEY, arr.toString())
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            if (!isServerHost(url)) return emptyList()
            val now = System.currentTimeMillis()
            return cookies.values.filter { it.expiresAt > now }
        }

        @Synchronized
        fun clear() {
            cookies.clear()
            store.put(COOKIE_KEY, null)
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    val baseUrl: String get() = prefs.serverUrl

    private fun isServerHost(url: HttpUrl): Boolean = url.host == baseUrl.toHttpUrl().host

    fun url(path: String): String = if (path.startsWith("http")) path else baseUrl + path

    private fun request(path: String): Request.Builder {
        val b = Request.Builder().url(url(path)).header("User-Agent", "GCFLArchiveAdmin/${kr.co.gcflarchive.admin.BuildConfig.VERSION_NAME}")
        val key = session.masterKey
        if (key != null && isServerHost(url(path).toHttpUrl())) {
            // check_auth() reads X-Master-Auth-Key; the grade APIs read X-Upload-Key.
            b.header("X-Master-Auth-Key", key).header("X-Upload-Key", key)
        }
        return b
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** GET with offline fallback: on network failure the last good response is returned. */
    suspend fun get(path: String, cache: Boolean = true): Fetched = withContext(Dispatchers.IO) {
        try {
            val body = execute(request(path).get().build())
            if (cache) writeCache(path, body)
            Fetched(body, fromCache = false)
        } catch (e: IOException) {
            if (e is ApiError || e is AuthException || !cache) throw e
            readCache(path) ?: throw e
        }
    }

    suspend fun postJson(path: String, body: JSONObject): JSONObject = send(path, "POST", body)
    suspend fun putJson(path: String, body: JSONObject): JSONObject = send(path, "PUT", body)
    suspend fun deleteJson(path: String, body: JSONObject = JSONObject()): JSONObject = send(path, "DELETE", body)

    suspend fun postForm(path: String, fields: Map<String, String>): JSONObject = write {
        val form = okhttp3.FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        parse(execute(request(path).post(form).build()))
    }

    suspend fun postMultipart(path: String, fields: Map<String, String>, files: List<Triple<String, String, RequestBody>>): JSONObject = write {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (k, v) -> addFormDataPart(k, v) }
            files.forEach { (field, name, data) -> addFormDataPart(field, name, data) }
        }.build()
        parse(execute(request(path).post(body).build()))
    }

    /** Raw bytes (e.g. PDF preview); not cached. */
    suspend fun bytes(path: String, body: JSONObject? = null): ByteArray = withContext(Dispatchers.IO) {
        val req = request(path).apply { if (body != null) post(body.toString().toRequestBody(JSON)) }.build()
        client.newCall(req).execute().use { res ->
            if (res.code == 401 || res.code == 403) throw AuthException(errorOf(res.body?.string().orEmpty()) ?: "권한이 없습니다.")
            if (!res.isSuccessful) throw ApiError(res.code, errorOf(res.body?.string().orEmpty()) ?: "HTTP ${res.code}")
            res.body?.bytes() ?: ByteArray(0)
        }
    }

    private suspend fun send(path: String, method: String, body: JSONObject): JSONObject = write {
        parse(execute(request(path).method(method, body.toString().toRequestBody(JSON)).build()))
    }

    private suspend fun <T> write(block: () -> T): T = withContext(Dispatchers.IO) {
        if (!isOnline()) throw OfflineException()
        block()
    }

    private fun execute(req: Request): String = client.newCall(req).execute().use { res ->
        val text = res.body?.string().orEmpty()
        when {
            res.code == 401 || res.code == 403 -> throw AuthException(errorOf(text) ?: "권한이 없습니다.")
            !res.isSuccessful -> throw ApiError(res.code, errorOf(text) ?: "HTTP ${res.code}")
        }
        text
    }

    private fun parse(text: String): JSONObject = runCatching { JSONObject(text) }.getOrElse { JSONObject().put("raw", text) }

    private fun errorOf(text: String): String? =
        runCatching { JSONObject(text).optString("error").ifBlank { JSONObject(text).optString("message") } }.getOrNull()?.ifBlank { null }

    // ── Offline cache (encrypted at rest; contains hakbuns and IPs) ─────────────

    private fun cacheFile(path: String) = File(cacheDir, path.hashCode().toUInt().toString(16) + ".bin")

    private fun writeCache(path: String, body: String) {
        runCatching {
            cacheFile(path).writeText(JSONObject().put("t", System.currentTimeMillis()).put("b", store.encrypt(body)).toString())
        }
    }

    private fun readCache(path: String): Fetched? = runCatching {
        val o = JSONObject(cacheFile(path).readText())
        Fetched(store.decrypt(o.getString("b")), fromCache = true, cachedAt = o.getLong("t"))
    }.getOrNull()

    fun clearAll() {
        cookieJar.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val COOKIE_KEY = "cookies"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        @Volatile private var instance: AdminApi? = null

        fun get(context: Context): AdminApi =
            instance ?: synchronized(this) { instance ?: AdminApi(context.applicationContext).also { instance = it } }

        /** Called after the server address changes so cookies/cache don't leak across hosts. */
        fun reset() {
            instance = null
        }
    }
}
