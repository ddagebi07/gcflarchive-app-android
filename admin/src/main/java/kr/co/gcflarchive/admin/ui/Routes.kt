package kr.co.gcflarchive.admin.ui

import androidx.fragment.app.Fragment
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.Grants
import kr.co.gcflarchive.admin.core.Permission
import kr.co.gcflarchive.admin.features.accounts.AdminsFragment
import kr.co.gcflarchive.admin.features.accounts.CustomAccountsFragment
import kr.co.gcflarchive.admin.features.accounts.TeachersFragment
import kr.co.gcflarchive.admin.features.content.AnswerSheetsFragment
import kr.co.gcflarchive.admin.features.content.DocumentsAdminFragment
import kr.co.gcflarchive.admin.features.content.MapRequestsFragment
import kr.co.gcflarchive.admin.features.content.MapReviewsFragment
import kr.co.gcflarchive.admin.features.content.PhotoAlbumsFragment
import kr.co.gcflarchive.admin.features.content.PhotoRolesFragment
import kr.co.gcflarchive.admin.features.content.PopupFragment
import kr.co.gcflarchive.admin.features.logs.AuditKind
import kr.co.gcflarchive.admin.features.logs.AuditLogFragment
import kr.co.gcflarchive.admin.features.logs.ShortLinksFragment
import kr.co.gcflarchive.admin.features.more.ExamSettingsFragment
import kr.co.gcflarchive.admin.features.more.GradeEntriesFragment
import kr.co.gcflarchive.admin.features.more.GradePinsFragment
import kr.co.gcflarchive.admin.features.more.GradePolicyFragment
import kr.co.gcflarchive.admin.features.more.GradeSettingsFragment
import kr.co.gcflarchive.admin.features.more.GradeTableFragment
import kr.co.gcflarchive.admin.features.more.SettingsFragment
import kr.co.gcflarchive.admin.features.more.ToolsFragment
import kr.co.gcflarchive.admin.features.security.HakbunBlocksFragment
import kr.co.gcflarchive.admin.features.security.IpBlocksFragment

enum class Tab { HOME, SECURITY, CONTENT, LOGS, MORE }

/**
 * Every admin destination with the permission that reveals it (보유 권한 외 항목 숨김).
 * Web-only tools carry a [webPath] and open in a Custom Tab instead of a fragment.
 */
enum class Route(
    val title: String,
    val desc: String,
    val icon: Int,
    val tab: Tab,
    val group: String,
    val visible: (Grants) -> Boolean,
    val create: (() -> Fragment)? = null,
    val webPath: String? = null,
) {
    // ② 보안·차단
    IP_BLOCKS("IP 차단", "차단 대역·자동 차단 설정", R.drawable.ic_block, Tab.SECURITY, "보안", { it.has(Permission.BLOCKED_IPS) }, { IpBlocksFragment() }),
    HAKBUN_BLOCKS("학번 차단", "로그인 차단 학번", R.drawable.ic_person, Tab.SECURITY, "보안", { it.has(Permission.BLOCKED_HAKBUNS) }, { HakbunBlocksFragment() }),
    BLOCK_LOG("차단 시도 로그", "차단된 학번의 로그인 시도", R.drawable.ic_history, Tab.SECURITY, "보안", { it.has(Permission.AUDITS) }, { AuditLogFragment.of(AuditKind.AUTH_BLOCK) }),

    // ③ 콘텐츠
    POPUP("팝업 공지", "메인 팝업 노출·기간·본문", R.drawable.ic_campaign, Tab.CONTENT, "공지", { it.has(Permission.POPUP) }, { PopupFragment() }),
    DOCUMENTS("자료", "PDF·영상·호스팅 파일 관리, 간편 업로드", R.drawable.ic_pdf, Tab.CONTENT, "자료", { it.hasAny(Permission.PDFS, Permission.VIDEOS) }, { DocumentsAdminFragment() }),
    ANSWER_SHEETS("정답지", "기출 정답지 입력 (객관식 40문항·서술형)", R.drawable.ic_file, Tab.CONTENT, "자료", { it.has(Permission.PDFS) }, { AnswerSheetsFragment() }),
    PHOTO_ALBUMS("사진첩", "앨범 목록·수정", R.drawable.ic_photo, Tab.CONTENT, "사진첩", { it.has(Permission.PHOTOS) }, { PhotoAlbumsFragment() }),
    PHOTO_ROLES("사진첩 역할 배정", "역할별 학번 배정", R.drawable.ic_person, Tab.CONTENT, "사진첩", { it.has(Permission.PHOTOS) }, { PhotoRolesFragment() }),
    MAP_REQUESTS("지도 요청 처리", "신규 장소·영업시간 제안 승인/반려", R.drawable.ic_map, Tab.CONTENT, "지도", { it.canModerateMap() }, { MapRequestsFragment() }),
    MAP_REVIEWS("지도 리뷰", "리뷰 확인·삭제", R.drawable.ic_notice, Tab.CONTENT, "지도", { it.canModerateMap() }, { MapReviewsFragment() }),

    // ④ 로그·계정
    ACCESS_LOG("접근·활동 로그", "열람·다운로드 기록 (기간·IP/학번 검색)", R.drawable.ic_history, Tab.LOGS, "감사 로그", { it.has(Permission.AUDITS) }, { AuditLogFragment.of(AuditKind.ACCESS) }),
    SHORT_LINKS("단축 링크 현황", "발급된 링크·사용자", R.drawable.ic_link, Tab.LOGS, "감사 로그", { it.has(Permission.AUDITS) }, { ShortLinksFragment() }),
    SHORT_LINK_LOG("단축 링크 사용 기록", "링크 사용·삭제 기록", R.drawable.ic_link, Tab.LOGS, "감사 로그", { it.has(Permission.AUDITS) }, { AuditLogFragment.of(AuditKind.SHORT_LINK) }),
    DRIVE_LOG("극플드라이브 기록", "업로드·다운로드·삭제 기록", R.drawable.ic_upload, Tab.LOGS, "감사 로그", { it.has(Permission.AUDITS) }, { AuditLogFragment.of(AuditKind.DRIVE) }),
    SCHOOL_IP_LOG("학교 IP 호출 로그", "학교 PC의 극플드라이브 API 호출", R.drawable.ic_history, Tab.LOGS, "감사 로그", { it.has(Permission.AUDITS) }, { AuditLogFragment.of(AuditKind.SCHOOL_IP) }),
    CONSENT_LOG("가입(개인정보 동의) 기록", "동의·면제 기록", R.drawable.ic_check, Tab.LOGS, "감사 로그", { it.has(Permission.AUDITS) }, { AuditLogFragment.of(AuditKind.CONSENT) }),
    ADMINS("관리자 계정", "권한 체크리스트로 추가·수정·삭제", R.drawable.ic_shield, Tab.LOGS, "계정", { it.has(Permission.CREDENTIALS) }, { AdminsFragment() }),
    CUSTOM_ACCOUNTS("특수 사용자 계정", "60000~99999 학번 계정", R.drawable.ic_person, Tab.LOGS, "계정", { it.has(Permission.CREDENTIALS) }, { CustomAccountsFragment() }),
    TEACHERS("교사 프로필", "별칭·연락처·메모", R.drawable.ic_person, Tab.LOGS, "계정", { it.has(Permission.CREDENTIALS) }, { TeachersFragment() }),

    // ⑤ 더보기
    GRADE_SETTINGS("등급컷 공개 조건", "모드·최소 인원·연산자·공개 시각", R.drawable.ic_grade, Tab.MORE, "등급컷 서비스", { it.has(Permission.GRADE) }, { GradeSettingsFragment() }),
    GRADE_TABLE("등급컷 표 / 오답률", "과목별 조회, 이미지 저장", R.drawable.ic_grade, Tab.MORE, "등급컷 서비스", { it.has(Permission.GRADE) }, { GradeTableFragment() }),
    GRADE_PINS("익명 PIN", "조회·삭제", R.drawable.ic_lock, Tab.MORE, "등급컷 서비스", { it.has(Permission.GRADE) }, { GradePinsFragment() }),
    GRADE_ENTRIES("성적 기록", "조회·삭제, 점수 수동 입력", R.drawable.ic_history, Tab.MORE, "등급컷 서비스", { it.has(Permission.GRADE) }, { GradeEntriesFragment() }),
    GRADE_POLICY("시험 정책·문항 수", "읽기 전용 (편집은 웹)", R.drawable.ic_info, Tab.MORE, "등급컷 서비스", { it.has(Permission.GRADE) }, { GradePolicyFragment() }),
    TOOLS("미니서비스", "등록 목록 조회·삭제 (등록은 웹)", R.drawable.ic_build, Tab.MORE, "사이트", { it.has(Permission.CREDENTIALS) }, { ToolsFragment() }),
    EXAM_SETTINGS("기출 과목·학과·면책조항", "필터 과목 그룹, 학과, 면책조항 PDF", R.drawable.ic_settings, Tab.MORE, "사이트", { it.has(Permission.PDFS) }, { ExamSettingsFragment() }),
    WEB_CRAWLER("아카이브 수집 & DB 구축", "웹에서 열기", R.drawable.ic_open_in_browser, Tab.MORE, "웹 전용 도구", { it.isUltimate }, webPath = "/admin/crawl"),
    WEB_HEADER("헤더 메뉴·유틸리티 사이트 편집", "웹에서 열기", R.drawable.ic_open_in_browser, Tab.MORE, "웹 전용 도구", { it.isUltimate }, webPath = "/admin_sites"),
    WEB_MATCH("학번↔접속기록 매칭 분석", "웹에서 열기", R.drawable.ic_open_in_browser, Tab.MORE, "웹 전용 도구", { it.has(Permission.AUDITS) }, webPath = "/admin/logs"),
    WEB_UPLOAD("대량 업로드", "웹에서 열기", R.drawable.ic_open_in_browser, Tab.MORE, "웹 전용 도구", { it.hasAny(Permission.PDFS, Permission.VIDEOS) }, webPath = "/admin/upload"),
    SETTINGS("앱 설정", "알림, 다크 모드, 서버 주소, 로그아웃", R.drawable.ic_settings, Tab.MORE, "앱", { true }, { SettingsFragment() });

    val key: String get() = name.lowercase()

    companion object {
        fun of(key: String?): Route? = entries.firstOrNull { it.key == key }
        fun forTab(tab: Tab, grants: Grants) = entries.filter { it.tab == tab && it.visible(grants) }
    }
}
