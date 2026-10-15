package kr.co.gcflarchive.student.school

import android.webkit.CookieManager
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import io.ktor.http.renderSetCookieHeader
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.net.SiteCookies

/**
 * The Android user app keeps its site session in android.webkit.CookieManager (app/data/SiteApi).
 * Reading and writing the same store means the shared screens use the app's login, and a login
 * done in a shared screen reaches the app too. Only our own host ever sees the cookies.
 */
class WebkitCookies : SiteCookies {
    private val manager: CookieManager get() = CookieManager.getInstance()

    override suspend fun get(requestUrl: Url): List<Cookie> {
        if (!Config.isOwnHost(requestUrl.toString())) return emptyList()
        val raw = manager.getCookie(Config.BASE_URL) ?: return emptyList()
        return raw.split(';').mapNotNull { part ->
            val kv = part.trim()
            val i = kv.indexOf('=')
            if (i <= 0) null else Cookie(kv.substring(0, i), kv.substring(i + 1), encoding = CookieEncoding.RAW)
        }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if (!Config.isOwnHost(requestUrl.toString())) return
        manager.setCookie(Config.BASE_URL, renderSetCookieHeader(cookie.copy(encoding = CookieEncoding.RAW)))
        manager.flush()
    }

    override suspend fun clear() {
        manager.removeAllCookies(null)
        manager.flush()
    }

    override fun close() = Unit
}
