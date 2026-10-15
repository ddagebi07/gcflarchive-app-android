package kr.co.gcflarchive.core.prefs

import com.russhwolf.settings.Settings
import kr.co.gcflarchive.core.meal.MealKind

/** App settings shared by every platform (급식 알림 등). */
class AppPrefs(private val settings: Settings = Settings()) {
    var mealNotifyEnabled: Boolean
        get() = settings.getBoolean(KEY_ENABLED, true)
        set(value) = settings.putBoolean(KEY_ENABLED, value)

    /** Minutes after midnight, local time. Default 07:30. */
    var mealNotifyMinuteOfDay: Int
        get() = settings.getInt(KEY_TIME, 7 * 60 + 30)
        set(value) = settings.putInt(KEY_TIME, value.coerceIn(0, 24 * 60 - 1))

    /** 점심 / 저녁, any of 0–2 (empty = no 급식 알림). Migrates the old single choice on first read. */
    var mealNotifyKinds: Set<MealKind>
        get() = MealKind.decode(settings.getStringOrNull(KEY_KINDS))
            ?: settings.getIntOrNull(KEY_KIND)?.let(MealKind::fromLegacy)
            ?: MealKind.DEFAULT
        set(value) = settings.putString(KEY_KINDS, MealKind.encode(value))

    /** 4-digit 등급컷 익명 접근 코드 shown once by the server, kept on this device. */
    var gradeGuestCode: String?
        get() = settings.getStringOrNull(KEY_GUEST_CODE)?.takeIf { it.length == 4 }
        set(value) = if (value == null) settings.remove(KEY_GUEST_CODE) else settings.putString(KEY_GUEST_CODE, value)

    /** Logged-in students in 등급컷: "verified" or "guest" (익명 chosen); null = ask. */
    var gradeAccessMode: String?
        get() = settings.getStringOrNull(KEY_GRADE_MODE)
        set(value) = if (value == null) settings.remove(KEY_GRADE_MODE) else settings.putString(KEY_GRADE_MODE, value)

    /** 새 공지 푸시 알림 (기본 켜짐). */
    var noticeNotifyEnabled: Boolean
        get() = settings.getBoolean(KEY_NOTICE, true)
        set(value) = settings.putBoolean(KEY_NOTICE, value)

    /** Server time of the last /api/notices/pushes check; null until the first one. */
    var noticePushSince: String?
        get() = settings.getStringOrNull(KEY_NOTICE_SINCE)
        set(value) = if (value == null) settings.remove(KEY_NOTICE_SINCE) else settings.putString(KEY_NOTICE_SINCE, value)

    /** 앱을 열 때 생체 인증 (기본 꺼짐). */
    var appLockEnabled: Boolean
        get() = settings.getBoolean(KEY_APP_LOCK, false)
        set(value) = settings.putBoolean(KEY_APP_LOCK, value)

    /** 로그인을 마친 적이 있고 아직 로그아웃되지 않았어요. 앱을 열 때 로그인 화면을 먼저 띄울지 정해요. */
    var signedIn: Boolean
        get() = settings.getBoolean(KEY_SIGNED_IN, false)
        set(value) = settings.putBoolean(KEY_SIGNED_IN, value)

    /** 시간표 칸에 과목 전체 이름(심화영어 Ⅱ)을 보여줘요. 기본은 줄임말(심F). */
    var timetableFullNames: Boolean
        get() = settings.getBoolean(KEY_TT_FULL, false)
        set(value) = settings.putBoolean(KEY_TT_FULL, value)

    /** 학교생활 알림함(/api/notifications)에서 마지막으로 본 시각 (서버 UTC ISO). */
    var schoolAlertSince: String?
        get() = settings.getStringOrNull(KEY_SCHOOL_SINCE)
        set(value) = if (value == null) settings.remove(KEY_SCHOOL_SINCE) else settings.putString(KEY_SCHOOL_SINCE, value)

    /** FCM token of this device once push is set up; null = the app checks the inbox instead. */
    var pushToken: String?
        get() = settings.getStringOrNull(KEY_PUSH_TOKEN)
        set(value) = if (value == null) settings.remove(KEY_PUSH_TOKEN) else settings.putString(KEY_PUSH_TOKEN, value)

    /**
     * The server said it can push (FCM set up there) when this device last registered. Only then does
     * the app leave 학교생활 alerts to push; otherwise it shows them itself from the inbox.
     */
    var serverPushes: Boolean
        get() = settings.getBoolean(KEY_SERVER_PUSHES, false)
        set(value) = settings.putBoolean(KEY_SERVER_PUSHES, value)

    /** Random id for this install ("inbox:<id>" device when there is no FCM token). */
    val installId: String
        get() = settings.getStringOrNull(KEY_INSTALL_ID) ?: (1..24).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }
            .joinToString("").also { settings.putString(KEY_INSTALL_ID, it) }

    val mealNotifyTimeLabel: String
        get() = "${(mealNotifyMinuteOfDay / 60).toString().padStart(2, '0')}:${(mealNotifyMinuteOfDay % 60).toString().padStart(2, '0')}"

    private companion object {
        const val KEY_SIGNED_IN = "signed_in"
        const val KEY_SERVER_PUSHES = "server_pushes"
        const val KEY_TT_FULL = "timetable_full_names"
        const val KEY_ENABLED = "meal_notify_enabled"
        const val KEY_TIME = "meal_notify_minute"
        const val KEY_KIND = "meal_notify_kind" // legacy single choice
        const val KEY_KINDS = "meal_notify_kinds"
        const val KEY_GUEST_CODE = "grade_guest_code"
        const val KEY_GRADE_MODE = "grade_access_mode"
        const val KEY_APP_LOCK = "app_lock_enabled"
        const val KEY_NOTICE = "notice_notify_enabled"
        const val KEY_SCHOOL_SINCE = "school_alert_since"
        const val KEY_PUSH_TOKEN = "push_token"
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_NOTICE_SINCE = "notice_push_since"
    }
}
