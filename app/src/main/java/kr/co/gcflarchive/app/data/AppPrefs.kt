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

    var onboardingShown: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING, value) }

    private companion object {
        const val KEY_ENABLED = "meal_notify_enabled"
        const val KEY_TIME = "meal_notify_minute"
        const val KEY_KIND = "meal_notify_kind" // legacy single choice
        const val KEY_KINDS = "meal_notify_kinds"
        const val KEY_LAST = "meal_last_notified"
        const val KEY_ONBOARDING = "onboarding_shown"
    }
}
