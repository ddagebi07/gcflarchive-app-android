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

    val mealNotifyTimeLabel: String
        get() = "${(mealNotifyMinuteOfDay / 60).toString().padStart(2, '0')}:${(mealNotifyMinuteOfDay % 60).toString().padStart(2, '0')}"

    private companion object {
        const val KEY_ENABLED = "meal_notify_enabled"
        const val KEY_TIME = "meal_notify_minute"
        const val KEY_KIND = "meal_notify_kind" // legacy single choice
        const val KEY_KINDS = "meal_notify_kinds"
        const val KEY_GUEST_CODE = "grade_guest_code"
    }
}
