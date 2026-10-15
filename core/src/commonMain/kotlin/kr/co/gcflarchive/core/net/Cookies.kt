package kr.co.gcflarchive.core.net

import com.russhwolf.settings.Settings
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kr.co.gcflarchive.core.Config

/**
 * Persistent cookie jar for the site session (Flask `gcfl_session`), stored in the
 * platform key-value store so "로그인 유지" survives restarts. Only cookies for our own
 * host are kept or sent; thumbnails on other hosts never see the session.
 */
/** The site session's cookie store. Android's user app plugs in its WebKit cookie store so one login covers both. */
interface SiteCookies : CookiesStorage {
    /** 로그아웃: forget every site cookie. */
    suspend fun clear()
}

class PersistentCookies(private val settings: Settings, private val key: String = "site_cookies") : SiteCookies {
    @Serializable
    private data class Stored(val name: String, val value: String, val expiresAt: Long?, val path: String)

    private val mutex = Mutex()
    private var cache: MutableList<Stored>? = null

    private fun load(): MutableList<Stored> = cache ?: runCatching {
        lenientJson.decodeFromString<List<Stored>>(settings.getString(key, "[]"))
    }.getOrDefault(emptyList()).toMutableList().also { cache = it }

    private fun persist(list: List<Stored>) {
        settings.putString(key, lenientJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Stored.serializer()), list))
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        if (!Config.isOwnHost(requestUrl.toString())) return emptyList()
        val list = load()
        val alive = list.filter { it.expiresAt == null || it.expiresAt > now() }
        if (alive.size != list.size) { list.retainAll(alive.toSet()); persist(list) }
        alive.filter { requestUrl.encodedPath.startsWith(it.path) }.map { Cookie(it.name, it.value, path = it.path) }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = mutex.withLock {
        if (!Config.isOwnHost(requestUrl.toString())) return
        val list = load()
        list.removeAll { it.name == cookie.name && it.path == (cookie.path ?: "/") }
        val expiresAt = when {
            cookie.maxAge != null -> now() + cookie.maxAge!! * 1000L
            cookie.expires != null -> cookie.expires!!.timestamp
            else -> null
        }
        if (cookie.value.isNotEmpty() && (expiresAt == null || expiresAt > now())) {
            list += Stored(cookie.name, cookie.value, expiresAt, cookie.path ?: "/")
        }
        persist(list)
    }

    /** 로그아웃: forget every site cookie. */
    override suspend fun clear() = mutex.withLock {
        cache = mutableListOf()
        persist(emptyList())
    }

    /** "name=value; …" for platform downloaders that need the session (e.g. PDF pages). */
    suspend fun header(): String = get(Url(Config.BASE_URL + "/")).joinToString("; ") { "${it.name}=${it.value}" }

    override fun close() {}
}
