package kr.co.gcflarchive.admin.features.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityAdminTest {
    @Test
    fun labelsEveryLoggedAction() {
        // Every action routes/community.py logs has a Korean label.
        val server = listOf(
            "view_post", "create_post", "edit_post", "delete_post", "create_comment", "edit_comment", "delete_comment",
            "like_post", "unlike_post", "report", "auto_blind", "filter_blocked", "moderate_restore", "moderate_delete",
            "moderate_blind", "board_create", "board_update", "board_delete", "settings_update", "filter_add",
            "filter_replace", "filter_remove",
        )
        server.forEach { assertTrue(it, CommunityAdmin.actionLabel(it) != it) }
        assertEquals("unknown_action", CommunityAdmin.actionLabel("unknown_action"))
    }

    @Test
    fun groupsFilterLogs() {
        assertTrue(CommunityAdmin.inGroup("all", "view_post"))
        assertTrue(CommunityAdmin.inGroup("write", "create_comment"))
        assertFalse(CommunityAdmin.inGroup("write", "like_post"))
        assertTrue(CommunityAdmin.inGroup("safety", "auto_blind"))
        assertTrue(CommunityAdmin.inGroup("admin", "board_update"))
    }

    @Test
    fun formatsTimesAndDetails() {
        assertEquals("10월 14일 17:05", CommunityAdmin.time("2026-10-14T08:05:00.123456Z"))
        assertEquals("not a time", CommunityAdmin.time("not a time"))
        assertEquals(
            "제목: 안녕 · 금칙어: 바보, 멍청이",
            CommunityAdmin.detailLine(mapOf("title" to "안녕", "words" to "바보, 멍청이")),
        )
    }

    @Test
    fun validatesBoardIdsAndWords() {
        assertNull(CommunityAdmin.boardIdError("club_2"))
        assertTrue(CommunityAdmin.boardIdError("C") != null)
        assertTrue(CommunityAdmin.boardIdError("한글") != null)
        assertEquals(listOf("바보", "멍청이"), CommunityAdmin.splitWords("바보, 멍청이\n바보\n "))
        assertEquals("로그인한 사람", CommunityAdmin.scopeLabel("login"))
    }
}
