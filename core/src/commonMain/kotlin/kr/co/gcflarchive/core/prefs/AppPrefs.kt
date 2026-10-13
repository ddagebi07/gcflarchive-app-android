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

    var mealNotifyKind: MealKind
        get() = MealKind.entries.getOrNull(settings.getInt(KEY_KIND, MealKind.ALL.ordinal)) ?: MealKind.ALL
        set(value) = settings.putInt(KEY_KIND, value.ordinal)

    /** 4-digit 등급컷 익명 접근 코드 shown once by the server, kept on this device. */
    var gradeGuestCode: String?
        get() = settings.getStringOrNull(KEY_GUEST_CODE)?.takeIf { it.length == 4 }
        set(value) = if (value == null) settings.remove(KEY_GUEST_CODE) else settings.putString(KEY_GUEST_CODE, value)

    val mealNotifyTimeLabel: String
        get() = "${(mealNotifyMinuteOfDay / 60).toString().padStart(2, '0')}:${(mealNotifyMinuteOfDay % 60).toString().padStart(2, '0')}"

    private companion object {
        const val KEY_ENABLED = "meal_notify_enabled"
        const val KEY_TIME = "meal_notify_minute"
        const val KEY_KIND = "meal_notify_kind"
        const val KEY_GUEST_CODE = "grade_guest_code"
    }
}
