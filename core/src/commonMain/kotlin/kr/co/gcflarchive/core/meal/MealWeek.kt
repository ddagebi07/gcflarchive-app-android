package kr.co.gcflarchive.core.meal

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kr.co.gcflarchive.core.i18n.I18nDates

/** 급식 탭의 주 단위(월~금) 이동 규칙. Android 앱의 app/meal/MealWeek.kt와 같은 규칙입니다. */
object MealWeek {
    const val DAYS = 5
    val WEEKDAYS = listOf("월", "화", "수", "목", "금")

    /** 주말은 다음 월요일로 (급식이 있는 날만 고를 수 있게). */
    fun schoolDay(d: LocalDate): LocalDate = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> d.plus(DatePeriod(days = 2))
        DayOfWeek.SUNDAY -> d.plus(DatePeriod(days = 1))
        else -> d
    }

    fun monday(d: LocalDate): LocalDate = d.minus(DatePeriod(days = d.dayOfWeek.isoDayNumber - 1))

    fun days(monday: LocalDate): List<LocalDate> = (0 until DAYS).map { monday.plus(DatePeriod(days = it)) }

    fun plusWeeks(d: LocalDate, weeks: Int): LocalDate = d.plus(DatePeriod(days = 7 * weeks))

    /** [base] 주를 0으로 했을 때 [d]가 속한 주의 번호. */
    fun weekIndex(base: LocalDate, d: LocalDate): Int = monday(base).daysUntil(monday(d)) / 7

    /** "10월 12일 – 16일", 달이 바뀌면 "9월 28일 – 10월 2일", 다른 해면 앞에 연도. 영어: "OCT 12 – 16" (I18nDates). */
    fun label(monday: LocalDate, today: LocalDate): String {
        val friday = monday.plus(DatePeriod(days = DAYS - 1))
        val range = I18nDates.range(monday.monthNumber, monday.dayOfMonth, friday.monthNumber, friday.dayOfMonth)
        return if (monday.year != today.year) I18nDates.withYear(monday.year, range) else range
    }

    private val ALL_WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

    /** 고른 날의 큰 제목: "10월 14일(수)", 오늘이면 "오늘 · 10월 14일(수)", 다른 해면 앞에 연도. 영어: "Wed, OCT 14". */
    fun dayTitle(d: LocalDate, today: LocalDate): String {
        val md = I18nDates.monthDay(d.monthNumber, d.dayOfMonth)
        val day = I18nDates.withWeekday(if (d.year != today.year) I18nDates.withYear(d.year, md) else md, ALL_WEEKDAYS[d.dayOfWeek.ordinal])
        return if (d == today) I18nDates.today(day) else day
    }
}
