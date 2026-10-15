package kr.co.gcflarchive.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    private fun build(name: String, code: Int? = null) = LatestBuild(name, code, "", "/beta")

    @Test
    fun parsesLatest() {
        val b = UpdateLogic.parse("""{"latest":{"versionName":"1.4.0","versionCode":"14","notes":"지도 개선","page":"/beta"}}""")!!
        assertEquals("1.4.0", b.versionName)
        assertEquals(14, b.versionCode)
        assertNull(UpdateLogic.parse("""{"latest":null}"""))
        assertNull(UpdateLogic.parse("not json"))
        assertNull(UpdateLogic.parse("""{"latest":{"versionCode":"3"}}"""))
    }

    @Test
    fun comparesVersions() {
        assertTrue(UpdateLogic.isNewer(build("1.10.0"), "1.9.3", 1))
        assertFalse(UpdateLogic.isNewer(build("1.0.0"), "1.0.0", 1))
        assertFalse(UpdateLogic.isNewer(build("1.0"), "1.0.0", 1))
        assertTrue(UpdateLogic.isNewer(build("1.0.0", code = 2), "1.0.0", 1)) // code wins
        assertFalse(UpdateLogic.isNewer(build("2.0.0", code = 1), "1.0.0", 1))
    }

    @Test
    fun skipsTheVersionTheUserPostponed() {
        assertTrue(UpdateLogic.shouldPrompt(build("1.1.0"), "1.0.0", 1, null))
        assertFalse(UpdateLogic.shouldPrompt(build("1.1.0"), "1.0.0", 1, "1.1.0"))
        assertTrue(UpdateLogic.shouldPrompt(build("1.2.0"), "1.0.0", 1, "1.1.0"))
        assertFalse(UpdateLogic.shouldPrompt(null, "1.0.0", 1, null))
    }
}
