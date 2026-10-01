package kr.co.gcflarchive.app.data

import android.content.Context
import androidx.core.content.edit

class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gcfl_prefs", Context.MODE_PRIVATE)

    var mealNotifyEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** Minutes after midnight, local time. Default 07:30. */
    var mealNotifyMinuteOfDay: Int
        get() = prefs.getInt(KEY_TIME, 7 * 60 + 30)
        set(value) = prefs.edit { putInt(KEY_TIME, value) }

    var mealNotifyKind: MealKind
        get() = MealKind.entries.getOrNull(prefs.getInt(KEY_KIND, MealKind.ALL.ordinal)) ?: MealKind.ALL
        set(value) = prefs.edit { putInt(KEY_KIND, value.ordinal) }

    /** yyyyMMdd of the last day a meal notification was posted, to avoid duplicates. */
    var lastNotifiedDate: String?
        get() = prefs.getString(KEY_LAST, null)
        set(value) = prefs.edit { putString(KEY_LAST, value) }

    var onboardingShown: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING, value) }

    enum class MealKind(val neisName: String?) {
        ALL(null),
        BREAKFAST("조식"),
        LUNCH("중식"),
        DINNER("석식"),
    }

    private companion object {
        const val KEY_ENABLED = "meal_notify_enabled"
        const val KEY_TIME = "meal_notify_minute"
        const val KEY_KIND = "meal_notify_kind"
        const val KEY_LAST = "meal_last_notified"
        const val KEY_ONBOARDING = "onboarding_shown"
    }
}
