package kr.co.gcflarchive.admin.features.community

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Labels and formatting for the community admin screens (pure, unit-tested). */
object CommunityAdmin {
    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

    /** routes/community.py SCOPES, in order. */
    val SCOPES = listOf(
        "all" to "누구나",
        "login" to "로그인한 사람",
        "students" to "학생",
        "teachers" to "선생님",
        "admins" to "관리자",
    )

    fun scopeLabel(scope: String): String = SCOPES.firstOrNull { it.first == scope }?.second ?: scope

    private val ACTIONS = mapOf(
        "view_post" to "글 보기",
        "create_post" to "글 올림",
        "edit_post" to "글 고침",
        "delete_post" to "글 지움",
        "create_comment" to "댓글 올림",
        "edit_comment" to "댓글 고침",
        "delete_comment" to "댓글 지움",
        "like_post" to "추천",
        "unlike_post" to "추천 취소",
        "report" to "신고",
        "auto_blind" to "신고 누적으로 숨김",
        "filter_blocked" to "금칙어로 막힘",
        "moderate_restore" to "다시 보이게 함",
        "moderate_delete" to "관리자가 지움",
        "moderate_blind" to "관리자가 숨김",
        "board_create" to "게시판 만듦",
        "board_update" to "게시판 고침",
        "board_delete" to "게시판 지움",
        "settings_update" to "운영 설정 바꿈",
        "filter_add" to "금칙어 추가",
        "filter_replace" to "금칙어 목록 바꿈",
        "filter_remove" to "금칙어 뺌",
        "nickname_set" to "닉네임 정함",
        "nickname_change" to "닉네임 바꿈",
    )

    fun actionLabel(action: String): String = ACTIONS[action] ?: action

    /** Filter chips on the log screen: key to label; each key lists its actions. */
    val LOG_GROUPS: List<Triple<String, String, Set<String>>> = listOf(
        Triple("all", "전체", emptySet()),
        Triple("write", "글·댓글", setOf("create_post", "edit_post", "delete_post", "create_comment", "edit_comment", "delete_comment")),
        Triple("react", "조회·추천", setOf("view_post", "like_post", "unlike_post")),
        Triple("safety", "신고·금칙어", setOf("report", "auto_blind", "filter_blocked")),
        Triple("nickname", "닉네임", setOf("nickname_set", "nickname_change")),
        Triple("admin", "관리", setOf(
            "moderate_restore", "moderate_delete", "moderate_blind", "board_create", "board_update", "board_delete",
            "settings_update", "filter_add", "filter_replace", "filter_remove",
        )),
    )

    fun inGroup(group: String, action: String): Boolean {
        val actions = LOG_GROUPS.firstOrNull { it.first == group }?.third ?: return true
        return actions.isEmpty() || action in actions
    }

    /** "10월 14일 17:05" in Korean time; the raw value when it isn't a timestamp. */
    fun time(iso: String): String =
        runCatching { Instant.parse(iso).atZone(SEOUL).format(TIME) }.getOrDefault(iso)

    /** One readable line from a log's detail JSON fields (title/content/words/reason…). */
    fun detailLine(detail: Map<String, String>): String {
        val parts = mutableListOf<String>()
        detail["title"]?.takeIf { it.isNotBlank() }?.let { parts += "제목: $it" }
        detail["content"]?.takeIf { it.isNotBlank() }?.let { parts += it.replace('\n', ' ').take(80) }
        detail["words"]?.takeIf { it.isNotBlank() }?.let { parts += "금칙어: $it" }
        detail["reason"]?.takeIf { it.isNotBlank() }?.let { parts += "사유: $it" }
        detail["reportCount"]?.takeIf { it.isNotBlank() }?.let { parts += "신고 ${it}번" }
        detail["after"]?.takeIf { it.isNotBlank() && !it.startsWith("{") }?.let { after ->
            parts += detail["before"]?.takeIf { it.isNotBlank() }?.let { "$it → $after" } ?: after
        }
        return parts.joinToString(" · ")
    }

    /** Board id rule from routes/community.py admin_boards. */
    fun boardIdError(id: String): String? =
        if (Regex("[a-z0-9][a-z0-9_-]{1,39}").matches(id)) null else "영문 소문자·숫자·-·_로 2~40자를 입력해주세요."

    /** Comma or line separated words, trimmed and de-duplicated. */
    fun splitWords(text: String): List<String> =
        text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
