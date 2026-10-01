package kr.co.gcflarchive.admin

import kr.co.gcflarchive.admin.core.AnswerGrid
import kr.co.gcflarchive.admin.core.AppLock
import kr.co.gcflarchive.admin.core.Csv
import kr.co.gcflarchive.admin.core.Grants
import kr.co.gcflarchive.admin.core.IpBand
import kr.co.gcflarchive.admin.core.Permission
import kr.co.gcflarchive.admin.core.shortTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminLogicTest {

    @Test
    fun answerGridRoundTrip() {
        val cells = AnswerGrid.parse("12345 12345 (23)4.")
        assertEquals(40, cells.size)
        assertEquals(listOf("1", "2", "3", "4", "5"), cells.take(5))
        assertEquals("23", cells[10])
        assertEquals("4", cells[11])
        assertEquals("", cells[12])
        assertEquals("12345 12345 (23)4", AnswerGrid.serialize(cells))
    }

    @Test
    fun answerGridGapsKeepNumbering() {
        val cells = MutableList(40) { "" }
        cells[0] = "3"
        cells[2] = "15"
        assertEquals("3?(15)", AnswerGrid.serialize(cells))
        assertEquals("", AnswerGrid.serialize(List(40) { "" }))
        assertEquals("24", AnswerGrid.toggle("2", 4))
        assertEquals("", AnswerGrid.toggle("2", 2))
        assertEquals("3", AnswerGrid.toggle("?", 3))
    }

    @Test
    fun csvEscapesAndUnionsColumns() {
        val csv = Csv.build(
            listOf(mapOf("ip" to "1.2.3.4", "reason" to "a,b"), mapOf("ip" to "5.6.7.8", "note" to "say \"hi\"")),
            preferred = listOf("reason", "ip"),
        )
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertEquals("reason,ip,note", lines[0])
        assertEquals("\"a,b\",1.2.3.4,", lines[1])
        assertEquals(",5.6.7.8,\"say \"\"hi\"\"\"", lines[2])
    }

    @Test
    fun ipBands() {
        assertTrue(IpBand.isValid("203.0.113.0/24"))
        assertTrue(IpBand.isValid("203.0.113.7"))
        assertTrue(IpBand.isValid("203.0.113."))
        assertTrue(IpBand.isValid("2001:db8::/64"))
        assertFalse(IpBand.isValid("300.1.1.1"))
        assertFalse(IpBand.isValid("hello"))
        assertTrue(IpBand.isAuto("Auto-blocked: 3 suspicious requests within 60 min"))
        assertFalse(IpBand.isAuto("수동 차단"))
    }

    @Test
    fun grantsFollowCheckAuth() {
        val viewer = Grants(master = false, scopes = setOf("view_audits"))
        assertTrue(viewer.has(Permission.AUDITS))
        assertFalse(viewer.has(Permission.CREDENTIALS))
        assertFalse(viewer.canModerateMap())
        assertTrue(Grants(false, setOf("credentials")).canModerateMap())
        assertTrue(Grants(false, setOf("Ultimate")).has(Permission.REVIEWS))
        assertTrue(Grants(true, emptySet()).has(Permission.GRADE))
    }

    @Test
    fun relockPolicy() {
        val lock = AppLock(backgroundGraceMs = 30_000, idleMs = 300_000)
        assertTrue(lock.locked)
        lock.onUnlocked(0)
        lock.onBackground(1_000)
        lock.onForeground(20_000) // quick trip to the file picker
        assertFalse(lock.locked)
        lock.onBackground(30_000)
        lock.onForeground(100_000)
        assertTrue(lock.locked)
        lock.onUnlocked(100_000)
        lock.onInteraction(200_000)
        assertFalse(lock.checkIdle(400_000))
        assertTrue(lock.checkIdle(600_001))
    }

    @Test
    fun timeFormat() {
        assertEquals("2026.10.01 03:04", shortTime("2026-10-01T03:04:05.123Z"))
    }
}
