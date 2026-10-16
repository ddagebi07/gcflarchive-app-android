package kr.co.gcflarchive.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.meal.Meal
import kr.co.gcflarchive.app.meal.MealRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 홈 화면 위젯: 급식, 한 번에 한 끼 (메뉴가 잘리지 않게, 가운뎃점으로 이어 쓴 줄글).
 * 기본은 중식, 13:30(점심시간 뒤)부터 석식; 주말이면 다음 월요일 중식. ‹ › 로 끼니와 날짜를
 * 넘기고, 한 시간 동안 안 누르면 기본으로 돌아가요. Fetches NEIS itself (no login).
 * Tapping the menu opens the 급식 tab; when loading failed, tapping tries again instead.
 */
class MealWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                update(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RETRY) {
            val pending = goAsync()
            scope.launch {
                try {
                    showLoading(context.applicationContext)
                    update(context.applicationContext)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        val forward = when (intent.action) {
            ACTION_NEXT -> true
            ACTION_PREV -> false
            else -> return super.onReceive(context, intent)
        }
        val pending = goAsync()
        scope.launch {
            try {
                step(context.applicationContext, forward)
                update(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private const val PREFS = "meal_widget"
        private const val ACTION_NEXT = "kr.co.gcflarchive.app.widget.MEAL_NEXT"
        private const val ACTION_PREV = "kr.co.gcflarchive.app.widget.MEAL_PREV"
        private const val ACTION_RETRY = "kr.co.gcflarchive.app.widget.MEAL_RETRY"
        private const val TTL_MS = 60 * 60 * 1000L  // back to the default an hour after the last tap
        private val AFTER_LUNCH: LocalTime = LocalTime.of(13, 30)

        /** Re-draws every placed widget (e.g. at app start). No-op when none is on the home screen. */
        fun refresh(context: Context) {
            if (ids(context).isEmpty()) return
            scope.launch { update(context.applicationContext) }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MealWidget::class.java))

        private fun isSchoolDay(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

        private fun shiftSchoolDay(d: LocalDate, step: Long): LocalDate {
            var day = d.plusDays(step)
            while (!isSchoolDay(day)) day = day.plusDays(step)
            return day
        }

        /** 오늘 (주말이면 다음 월요일) 중식, or 석식 from 13:30 on a school day. */
        private fun defaultSlot(): Pair<LocalDate, String> {
            val today = MealRepository.today()
            if (!isSchoolDay(today)) return shiftSchoolDay(today, 1) to "중식"
            return today to if (LocalTime.now(SEOUL) >= AFTER_LUNCH) "석식" else "중식"
        }

        private data class Shown(val day: LocalDate, val meals: List<Meal>, val index: Int)

        /** The meal on screen now: a fresh stored slot (index -1 = that day's last meal) or the default. null = offline. */
        private suspend fun resolve(context: Context): Shown? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val fresh = System.currentTimeMillis() - prefs.getLong("navAt", 0) < TTL_MS
            val stored = prefs.getString("navDay", null)?.takeIf { fresh }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (stored != null) {
                val meals = runCatching { MealRepository.fetch(stored) }.getOrNull() ?: return null
                val i = prefs.getInt("navIndex", 0)
                return Shown(stored, meals, if (meals.isEmpty()) 0 else if (i < 0) meals.lastIndex else i.coerceAtMost(meals.lastIndex))
            }
            val (day, preferred) = defaultSlot()
            val meals = runCatching { MealRepository.fetch(day) }.getOrNull() ?: return null
            val i = meals.indexOfFirst { it.kind == preferred }.takeIf { it >= 0 } ?: if (preferred == "석식") maxOf(meals.lastIndex, 0) else 0
            return Shown(day, meals, i)
        }

        /** ‹ › : the next / previous meal, crossing into the next / previous school day. */
        private suspend fun step(context: Context, forward: Boolean) {
            val now = resolve(context)
            val day = now?.day ?: defaultSlot().first
            val count = now?.meals?.size ?: 0
            val index = now?.index ?: 0
            val (newDay, newIndex) = when {
                forward && index + 1 < count -> day to index + 1
                forward -> shiftSchoolDay(day, 1) to 0
                index > 0 && count > 0 -> day to index - 1
                else -> shiftSchoolDay(day, -1) to -1
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("navDay", newDay.toString()).putInt("navIndex", newIndex)
                .putLong("navAt", System.currentTimeMillis()).apply()
        }

        private suspend fun update(context: Context) {
            val shown = resolve(context)
            val day = shown?.day ?: defaultSlot().first
            val meal = shown?.meals?.getOrNull(shown.index)
            val title = listOfNotNull(dayLabel(day), meal?.kind).joinToString(" ")
            val body = when {
                shown == null -> context.getString(R.string.widget_meal_offline)
                meal == null -> context.getString(R.string.widget_meal_none)
                else -> meal.dishes.joinToString(" · ") { it.name }
            }
            val views = RemoteViews(context.packageName, R.layout.widget_meal).apply {
                setTextViewText(R.id.widgetMealTitle, title)
                setTextViewText(R.id.widgetMealBody, body)
                // 불러오지 못했으면 누를 때 앱을 여는 대신 다시 불러와요.
                setOnClickPendingIntent(R.id.widgetMealRoot, if (shown == null) stepIntent(context, ACTION_RETRY, 7004) else open(context))
                setOnClickPendingIntent(R.id.widgetMealPrev, stepIntent(context, ACTION_PREV, 7002))
                setOnClickPendingIntent(R.id.widgetMealNext, stepIntent(context, ACTION_NEXT, 7003))
            }
            val manager = AppWidgetManager.getInstance(context)
            ids(context).forEach { manager.updateAppWidget(it, views) }
        }

        /** Retry tapped: say so right away (the fetch can take a few seconds). */
        private fun showLoading(context: Context) {
            val views = RemoteViews(context.packageName, R.layout.widget_meal).apply {
                setTextViewText(R.id.widgetMealTitle, dayLabel(defaultSlot().first))
                setTextViewText(R.id.widgetMealBody, context.getString(R.string.widget_meal_loading))
            }
            val manager = AppWidgetManager.getInstance(context)
            ids(context).forEach { manager.partiallyUpdateAppWidget(it, views) }
        }

        /** '오늘' / '내일' / '10/20(월)' */
        private fun dayLabel(d: LocalDate): String {
            val today = MealRepository.today()
            return when (d) {
                today -> "오늘"
                today.plusDays(1) -> "내일"
                else -> "${d.monthValue}/${d.dayOfMonth}(${"월화수목금토일"[d.dayOfWeek.value - 1]})"
            }
        }

        private fun stepIntent(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
            context, code,
            Intent(context, MealWidget::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 7001,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_MEAL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
