package kr.co.gcflarchive.app.data

import android.content.Context
import androidx.core.content.edit
import kr.co.gcflarchive.app.meal.MealKind

class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gcfl_prefs", Context.MODE_PRIVATE)

    var mealNotifyEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** Minutes after midnight, local time. Default 07:30. */
    var mealNotifyMinuteOfDay: Int
        get() = prefs.getInt(KEY_TIME, 7 * 60 + 30)
        set(value) = prefs.edit { putInt(KEY_TIME, value) }

    /** 점심 / 저녁, any of 0–2 (empty = no 급식 알림). Migrates the old single choice on first read. */
    var mealNotifyKinds: Set<MealKind>
        get() = MealKind.decode(prefs.getString(KEY_KINDS, null))
            ?: (if (prefs.contains(KEY_KIND)) MealKind.fromLegacy(prefs.getInt(KEY_KIND, 0)) else null)
            ?: MealKind.DEFAULT
        set(value) = prefs.edit { putString(KEY_KINDS, MealKind.encode(value)) }

    /** yyyyMMdd of the last day a meal notification was posted, to avoid duplicates. */
    var lastNotifiedDate: String?
        get() = prefs.getString(KEY_LAST, null)
        set(value) = prefs.edit { putString(KEY_LAST, value) }

    /** 새 공지 푸시 알림 (기본 켜짐). */
    var noticeNotifyEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTICE, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTICE, value) }

    /** Server time of the last /api/notices/pushes check; null until the first one. */
    var noticePushSince: String?
        get() = prefs.getString(KEY_NOTICE_SINCE, null)
        set(value) = prefs.edit { putString(KEY_NOTICE_SINCE, value) }

    /** 업데이트 안내에서 '나중에'를 고른 버전 (그 버전은 다시 묻지 않아요). */
    var skippedUpdateVersion: String?
        get() = prefs.getString(KEY_SKIPPED_UPDATE, null)
        set(value) = prefs.edit { putString(KEY_SKIPPED_UPDATE, value) }

    /** 앱을 열 때 생체 인증 (기본 꺼짐). */
    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit { putBoolean(KEY_APP_LOCK, value) }

    var onboardingShown: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING, value) }

    /** 로그인을 마친 적이 있고 아직 로그아웃되지 않았어요. 앱을 열 때 로그인 화면을 먼저 띄울지 정해요. */
    var signedIn: Boolean
        get() = prefs.getBoolean(KEY_SIGNED_IN, false)
        set(value) = prefs.edit { putBoolean(KEY_SIGNED_IN, value) }

    private companion object {
        const val KEY_SIGNED_IN = "signed_in"
        const val KEY_ENABLED = "meal_notify_enabled"
        const val KEY_TIME = "meal_notify_minute"
        const val KEY_KIND = "meal_notify_kind" // legacy single choice
        const val KEY_KINDS = "meal_notify_kinds"
        const val KEY_LAST = "meal_last_notified"
        const val KEY_ONBOARDING = "onboarding_shown"
        const val KEY_APP_LOCK = "app_lock_enabled"
        const val KEY_NOTICE = "notice_notify_enabled"
        const val KEY_SKIPPED_UPDATE = "skipped_update_version"
        const val KEY_NOTICE_SINCE = "notice_push_since"
    }
}
