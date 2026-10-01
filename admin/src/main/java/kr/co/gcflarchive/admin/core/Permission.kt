package kr.co.gcflarchive.admin.core

/** Server-side admin scopes (app_core.ALL_SCOPED_ADMIN_PERMISSIONS + map's manage_reviews). */
enum class Permission(val key: String, val label: String) {
    PDFS("manage_pdfs", "자료(PDF) 관리"),
    VIDEOS("manage_videos", "영상 관리"),
    PHOTOS("manage_photos", "사진첩 관리"),
    POPUP("manage_popup", "팝업 공지"),
    BLOCKED_HAKBUNS("manage_blocked_hakbuns", "학번 차단"),
    BLOCKED_IPS("manage_blocked_ips", "IP 차단"),
    AUDITS("view_audits", "감사 로그"),
    CREDENTIALS("credentials", "계정·사이트 설정"),
    GRADE("grade", "등급컷 서비스"),
    REVIEWS("manage_reviews", "지도 요청·리뷰"),
    ULTIMATE("Ultimate", "최고 관리자 (모든 권한)");

    companion object {
        fun of(key: String): Permission? = entries.firstOrNull { it.key == key }

        /** Choices shown in the admin-account editor (matches the web admin page). */
        val ASSIGNABLE = listOf(PDFS, VIDEOS, PHOTOS, POPUP, BLOCKED_HAKBUNS, BLOCKED_IPS, AUDITS, CREDENTIALS, GRADE, REVIEWS, ULTIMATE)
    }
}

/**
 * What the signed-in operator may do. Mirrors check_auth(): the master key and the
 * Ultimate scope pass every check; otherwise the scope must be granted explicitly.
 */
data class Grants(val master: Boolean, val scopes: Set<String>) {
    val isUltimate: Boolean get() = master || Permission.ULTIMATE.key in scopes

    fun has(p: Permission): Boolean = isUltimate || p.key in scopes

    /** Map moderation endpoints accept manage_reviews or credentials. */
    fun canModerateMap(): Boolean = has(Permission.REVIEWS) || has(Permission.CREDENTIALS)

    fun hasAny(vararg ps: Permission): Boolean = ps.any { has(it) }

    companion object {
        val NONE = Grants(master = false, scopes = emptySet())
    }
}
