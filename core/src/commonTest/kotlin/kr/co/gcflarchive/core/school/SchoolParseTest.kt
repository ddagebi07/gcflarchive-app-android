package kr.co.gcflarchive.core.school

import kr.co.gcflarchive.core.net.parseObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shapes taken from routes/school_*.py responses (test_school.py on the server). */
class SchoolParseTest {
    @Test
    fun me() {
        val t = SchoolParse.me(parseObject("""{"loggedIn":true,"userId":"t1","role":"teacher","teacher":{"name":"김과천"},
            "canManageGroups":true,"unread":{"notifications":2,"visits":1,"substitutes":3,"messages":0}}"""))
        assertEquals(SchoolRole.TEACHER, t.role)
        assertEquals("김과천", t.teacherName)
        assertEquals(1, t.pendingVisits)
        assertEquals(3, t.pendingSubstitutes)
        assertEquals(SchoolRole.OTHER, SchoolParse.me(parseObject("""{"loggedIn":false,"role":"other"}""")).role)
    }

    @Test
    fun week() {
        val w = SchoolParse.week(parseObject("""{"kind":"student","title":"2학년 3반","hasTimetable":true,
            "days":[{"date":"2026-10-19","weekday":"월","isToday":false},{"date":"2026-10-20","weekday":"화","isToday":true}],
            "periods":[{"key":"1","label":"1교시","start":"08:40","end":"09:30","isClass":true},{"key":"방과후","label":"방과후","start":"16:30","end":"18:00","isClass":false}],
            "cells":[[{"subject":"문학","teachers":["이외고(영)"],"room":"","moving":false,"empty":false,"changed":true,"note":"보강 · 이외고(영) 선생님","place":""}],
                     [{"subject":"","teachers":[],"room":"","moving":false,"empty":true,"changed":false,"note":"","place":""}]]}"""))
        assertEquals(1, w.todayIndex)
        assertEquals(listOf("1"), w.classPeriods.map { it.key })
        val c = w.cell(0, w.classPeriods[0])
        assertTrue(c.changed)
        assertEquals("이외고(영)", c.detail)
        assertTrue(w.cell(1, w.classPeriods[0]).empty)
        val named = SchoolParse.week(parseObject("""{"days":[{"date":"2026-10-19","weekday":"월"}],"periods":[{"key":"1","isClass":true}],
            "cells":[[{"subject":"심F","fullName":"심화영어 Ⅱ","track":"영어과","empty":false}]]}"""))
        val n = named.cell(0, named.classPeriods[0])
        assertEquals("심화영어 Ⅱ (심F)", n.title)
        assertEquals("영어과", n.trackLabel)
        assertEquals("", c.copy(track = "전체과").trackLabel)
        assertTrue(w.cell(4, w.classPeriods[0]).empty) // missing day → empty
    }

    @Test
    fun visit() {
        val v = SchoolParse.visit(parseObject("""{"id":5,"studentKey":"20315","studentName":"홍길동","purpose":"대회 문의",
            "slots":[{"date":"2026-10-19","period":"2","label":"10월 19일(월) 2교시"},{"date":"2026-10-19","period":"방과후","label":"10월 19일(월) 방과후"}],
            "status":"accepted","acceptedIndex":1,"teacherNote":"과학관으로","teacherName":"최과학","location":"과학관","createdAt":"x",
            "threadId":null,"history":[{"type":"created","notified":true},{"type":"forwarded","fromName":"김과천","toName":"최과학","note":"","notified":false}]}"""))
        assertEquals(1, v.acceptedIndex)
        assertEquals("방문 수락", v.statusLabel)
        assertNull(v.threadId)
        assertEquals(1, v.forwards.size)
        assertFalse(v.forwards[0].notified)
        assertNull(SchoolParse.visit(parseObject("""{"acceptedIndex":null}""")).acceptedIndex)
    }

    @Test
    fun messageDetailSenderAndStudent() {
        val sent = SchoolParse.messageDetail(parseObject("""{"id":1,"title":"안내","readers":[{"hakbun":"20315","readAt":"x"}],
            "unread":["20401"],"files":[{"id":"ab","name":"안내.hwp","size":2097152,"url":"/api/school/files/ab"}],"byClass":true}"""))
        assertEquals(listOf("20315"), sent.readers)
        assertEquals("2.0MB", sent.files[0].sizeLabel)
        val mine = SchoolParse.messageDetail(parseObject("""{"id":1,"title":"안내","threadId":7,"read":false}"""))
        assertNull(mine.readers)
        assertEquals(7, mine.threadId)
        assertFalse(mine.summary.read)
    }

    @Test
    fun groupMembersText() {
        val g = SchoolParse.group(parseObject("""{"id":3,"kind":"private","name":"보충반","members":[{"hakbun":"20315","role":"부장"},{"hakbun":"20301","role":""}]}"""))
        assertTrue(g.isPrivate)
        assertEquals("20315 부장\n20301", g.membersText)
    }

    @Test
    fun targetsAndSlots() {
        assertEquals(AlertTarget(TargetKind.VISIT, 12), parseTarget("visit:12"))
        assertEquals(AlertTarget(TargetKind.TIMETABLE, null), parseTarget("timetable"))
        assertEquals(TargetKind.NONE, parseTarget("").kind)
        assertEquals(listOf(mapOf("date" to "2026-10-19", "period" to "2")),
            visitSlotsBody(listOf("2026-10-19" to "2", "" to "3", "2026-10-20" to "")))
        assertTrue(Audience().isEmpty)
        assertFalse(Audience(hakbuns = "20315").isEmpty)
    }
}
