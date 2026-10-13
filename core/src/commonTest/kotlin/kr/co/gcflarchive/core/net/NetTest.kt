package kr.co.gcflarchive.core.net

import com.russhwolf.settings.MapSettings
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetTest {
    private val site = Url("https://gcflarchive.co.kr/api/verify-status")

    @Test
    fun cookiesPersistOnlyForOwnHost() = runTest {
        val settings = MapSettings()
        val jar = PersistentCookies(settings)
        jar.addCookie(site, Cookie("gcfl_session", "abc", path = "/"))
        jar.addCookie(Url("https://evil.example.com/"), Cookie("x", "1"))

        // A fresh jar over the same storage (= app restart) still has the session.
        val restarted = PersistentCookies(settings)
        assertEquals(listOf("gcfl_session" to "abc"), restarted.get(site).map { it.name to it.value })
        assertTrue(restarted.get(Url("https://i.ytimg.com/vi/x.jpg")).isEmpty())
        assertEquals("gcfl_session=abc", restarted.header())

        restarted.clear()
        assertTrue(PersistentCookies(settings).get(site).isEmpty())
    }

    @Test
    fun expiredOrEmptyCookiesAreDropped() = runTest {
        val jar = PersistentCookies(MapSettings())
        jar.addCookie(site, Cookie("gcfl_session", "abc", maxAge = 0))
        jar.addCookie(site, Cookie("other", ""))
        assertTrue(jar.get(site).isEmpty())
    }

    @Test
    fun lenientJson() {
        val o = parseObject("""{"n":"12","d":"3.5","b":"true","nul":null,"arr":[1," a ",null],"o":{"k":1}}""")
        assertEquals(12, o.optInt("n"))
        assertEquals(3.5, o.optDouble("d"))
        assertTrue(o.optBoolean("b"))
        assertEquals("", o.optString("nul"))
        assertTrue(o.isNull("missing"))
        assertEquals(listOf("1", "a"), o.optJSONArray("arr").strings())
        assertEquals(1, o.optJSONObject("o")?.optInt("k"))
        assertEquals("""{"a":1,"b":[true,null],"c":{"d":"x"}}""", jsonOf("a" to 1, "b" to listOf(true, null), "c" to mapOf("d" to "x")).toString())
        assertTrue(parseObject("not json").keys().isEmpty())
    }
}
