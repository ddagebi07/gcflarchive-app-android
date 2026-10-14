package kr.co.gcflarchive.core.meal

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

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

    /** "10월 12일 – 16일", 달이 바뀌면 "9월 28일 – 10월 2일", 다른 해면 앞에 연도. */
    fun label(monday: LocalDate, today: LocalDate): String {
        val friday = monday.plus(DatePeriod(days = DAYS - 1))
        val start = "${monday.monthNumber}월 ${monday.dayOfMonth}일"
        val end = if (friday.monthNumber == monday.monthNumber) "${friday.dayOfMonth}일" else "${friday.monthNumber}월 ${friday.dayOfMonth}일"
        val year = if (monday.year != today.year) "${monday.year}년 " else ""
        return "$year$start – $end"
    }

    private val ALL_WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

    /** 고른 날의 큰 제목: "10월 14일(수)", 오늘이면 "오늘 · 10월 14일(수)", 다른 해면 앞에 연도. */
    fun dayTitle(d: LocalDate, today: LocalDate): String {
        val year = if (d.year != today.year) "${d.year}년 " else ""
        val day = "$year${d.monthNumber}월 ${d.dayOfMonth}일(${ALL_WEEKDAYS[d.dayOfWeek.ordinal]})"
        return if (d == today) "오늘 · $day" else day
    }
}
