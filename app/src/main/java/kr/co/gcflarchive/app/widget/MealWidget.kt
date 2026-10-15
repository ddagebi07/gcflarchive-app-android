package kr.co.gcflarchive.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.meal.Meal
import kr.co.gcflarchive.app.meal.MealRepository
import java.time.LocalDate

/**
 * 홈 화면 위젯: 오늘의 급식 (중식·석식 …). Fetches the menu itself (NEIS, no login needed),
 * refreshes about every hour and whenever the app starts, and keeps the last menu so it
 * still shows something offline. Tapping it opens the 급식 tab.
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

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val PREFS = "meal_widget"

        /** Re-draws every placed widget (e.g. at app start). No-op when none is on the home screen. */
        fun refresh(context: Context) {
            if (ids(context).isEmpty()) return
            scope.launch { update(context.applicationContext) }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MealWidget::class.java))

        private suspend fun update(context: Context) {
            val today = MealRepository.today()
            val meals = runCatching { MealRepository.fetch(today) }.getOrNull()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val body: CharSequence = when {
                meals == null -> prefs.getString("text_$today", null) ?: context.getString(R.string.widget_meal_offline)
                meals.isEmpty() -> context.getString(R.string.widget_meal_none)
                else -> menu(context, meals).also { prefs.edit().clear().putString("text_$today", it.toString()).apply() }
            }
            val views = RemoteViews(context.packageName, R.layout.widget_meal).apply {
                setTextViewText(R.id.widgetMealTitle, context.getString(R.string.widget_meal_title, dateLabel(today)))
                setTextViewText(R.id.widgetMealBody, body)
                setOnClickPendingIntent(R.id.widgetMealRoot, open(context))
            }
            val manager = AppWidgetManager.getInstance(context)
            ids(context).forEach { manager.updateAppWidget(it, views) }
        }

        /** '중식' (bold, blue) then its dishes, one meal per paragraph. */
        private fun menu(context: Context, meals: List<Meal>): CharSequence {
            val accent = context.getColor(R.color.krds_primary_text)
            val out = SpannableStringBuilder()
            meals.forEachIndexed { i, m ->
                if (i > 0) out.append("\n\n")
                val start = out.length
                out.append(m.kind)
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(ForegroundColorSpan(accent), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.append("\n").append(m.dishes.joinToString(" · ") { it.name })
            }
            return out
        }

        private fun dateLabel(d: LocalDate): String =
            "${d.monthValue}/${d.dayOfMonth}(${"월화수목금토일"[d.dayOfWeek.value - 1]})"

        private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 7001,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_MEAL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
