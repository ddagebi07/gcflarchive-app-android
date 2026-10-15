package kr.co.gcflarchive.student.meal

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDateTime
import kr.co.gcflarchive.core.Services
import kr.co.gcflarchive.core.meal.MealRepository
import kr.co.gcflarchive.student.platform.Notifications
import kr.co.gcflarchive.student.platform.ScheduledNotice

/**
 * 급식 알림 without a server: the next week of menus is fetched now and each school
 * day's menu is scheduled as a local notification at the chosen time. Re-run on every
 * app start and whenever the settings change, so the queue stays a week ahead.
 */
object MealAlarms {
    const val DAYS_AHEAD = 7

    suspend fun reschedule(services: Services) {
        val prefs = services.prefs
        if (!prefs.mealNotifyEnabled) {
            Notifications.cancelMealNotices()
            return
        }
        val today = MealRepository.today()
        val hour = prefs.mealNotifyMinuteOfDay / 60
        val minute = prefs.mealNotifyMinuteOfDay % 60
        val nowMinutes = MealRepository.nowHour() * 60
        val days = (0 until DAYS_AHEAD).map { MealRepository.shift(today, it) }
            .filterNot(MealRepository::isWeekend)
            // Today only if the time hasn't passed yet.
            .filter { it != today || prefs.mealNotifyMinuteOfDay > nowMinutes }
        // One notice per meal (중식, 석식 …) so neither gets cut off.
        val notices = coroutineScope {
            days.map { d ->
                async {
                    val meals = runCatching { MealRepository.forKinds(services.meals.fetch(d), prefs.mealNotifyKinds) }.getOrDefault(emptyList())
                    meals.mapIndexed { i, m ->
                        ScheduledNotice(
                            id = "${MealRepository.neisDate(d)}-$i",
                            at = LocalDateTime(d.year, d.monthNumber, d.dayOfMonth, hour, minute),
                            title = "오늘의 ${m.kind}" + (m.calories?.let { " · $it" } ?: ""),
                            body = m.dishes.joinToString("\n") { "· ${it.name}" },
                        )
                    }
                }
            }.awaitAll().flatten()
        }
        Notifications.replaceMealNotices(notices)
    }
}
