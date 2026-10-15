package kr.co.gcflarchive.admin.features.community

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Labels and formatting for the community admin screens (pure, unit-tested). */
object CommunityAdmin {
    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

    /** Basic scopes (services/community_roles.py BASE_SCOPES); the server also sends roles as "role:<id>". */
    val SCOPES = listOf(
        "all" to "누구나",
        "login" to "로그인한 사람",
        "students" to "학생",
        "teachers" to "선생님",
        "admins" to "관리자",
    )

    /** A board permission is a comma list; any one match is enough. */
    fun splitScope(scope: String): List<String> = scope.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** "학생, 1학년 영일과" using [options] (value to label) from the server, basic labels otherwise. */
    fun scopeLabel(scope: String, options: List<Pair<String, String>> = SCOPES): String =
        splitScope(scope).joinToString(", ") { t -> options.firstOrNull { it.first == t }?.second ?: SCOPES.firstOrNull { it.first == t }?.second ?: t }

    /** 학번 5자리 check for 포함/제외 lists; null when every value is fine. */
    fun hakbunListError(values: List<String>): String? =
        values.firstOrNull { !Regex("\\d{5}").matches(it) }?.let { "학번 5자리를 넣어주세요: $it" }

    /** 학번 패턴: digits, ? (one digit), * (the rest), up to 5 characters. */
    fun patternError(values: List<String>): String? =
        values.firstOrNull { !Regex("[0-9?*]{1,5}").matches(it) }?.let { "패턴은 숫자, ?, *로 5자리까지 입력해주세요: $it" }

    /** 반·번호 범위 like "01-02, 05"; blank is fine (no condition). */
    fun rangeError(text: String, what: String): String? {
        val parts = text.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        val ok = parts.all { p ->
            val m = Regex("(\\d{1,2})(?:[-~](\\d{1,2}))?").matchEntire(p) ?: return@all false
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].ifEmpty { m.groupValues[1] }.toInt()
            minOf(a, b) >= 1 && maxOf(a, b) <= 99
        }
        return if (ok) null else "${what}은(는) '01-02, 05'처럼 1~99 사이 숫자와 범위로 입력해주세요."
    }

    private val ACTIONS = mapOf(
        "view_post" to "글 보기",
        "create_post" to "글 올림",
        "edit_post" to "글 수정",
        "delete_post" to "글 삭제",
        "create_comment" to "댓글 올림",
        "edit_comment" to "댓글 수정",
        "delete_comment" to "댓글 삭제",
        "like_post" to "추천",
        "unlike_post" to "추천 취소",
        "poll_vote" to "투표",
        "poll_unvote" to "투표 취소",
        "report" to "신고",
        "auto_blind" to "신고 누적으로 숨김",
        "filter_blocked" to "금칙어로 막힘",
        "moderate_restore" to "다시 보이게 함",
        "moderate_delete" to "관리자가 삭제",
        "moderate_blind" to "관리자가 숨김",
        "board_create" to "게시판 만듦",
        "board_update" to "게시판 수정",
        "board_delete" to "게시판 삭제",
        "settings_update" to "운영 설정 바꿈",
        "role_create" to "역할 만듦",
        "role_update" to "역할 수정",
        "role_delete" to "역할 삭제",
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
        Triple("react", "조회·추천·투표", setOf("view_post", "like_post", "unlike_post", "poll_vote", "poll_unvote")),
        Triple("safety", "신고·금칙어", setOf("report", "auto_blind", "filter_blocked")),
        Triple("nickname", "닉네임", setOf("nickname_set", "nickname_change")),
        Triple("admin", "관리", setOf(
            "moderate_restore", "moderate_delete", "moderate_blind", "board_create", "board_update", "board_delete",
            "role_create", "role_update", "role_delete",
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
