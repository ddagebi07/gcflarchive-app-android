package kr.co.gcflarchive.app.meal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kr.co.gcflarchive.core.i18n.I18nDates

/** 급식 탭의 주 단위(월~금) 이동 규칙. */
object MealWeek {
    const val DAYS = 5

    /** 주말은 다음 월요일로 (급식이 있는 날만 고를 수 있게). */
    fun schoolDay(d: LocalDate): LocalDate = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> d.plusDays(2)
        DayOfWeek.SUNDAY -> d.plusDays(1)
        else -> d
    }

    fun monday(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    fun days(monday: LocalDate): List<LocalDate> = (0 until DAYS).map { monday.plusDays(it.toLong()) }

    /** 같은 요일을 유지한 채 [weeks]주 이동. */
    fun shiftWeeks(d: LocalDate, weeks: Long): LocalDate = schoolDay(d).plusWeeks(weeks)

    /** [base] 주를 0으로 했을 때 [d]가 속한 주의 번호. */
    fun weekIndex(base: LocalDate, d: LocalDate): Long = ChronoUnit.WEEKS.between(monday(base), monday(d))

    /** "10월 13일 – 17일", 달이 바뀌면 "10월 27일 – 11월 1일", 다른 해면 앞에 연도. */
    fun label(monday: LocalDate, today: LocalDate): String {
        val friday = monday.plusDays((DAYS - 1).toLong())
        // 영어 등은 번역 파일의 날짜 틀을 따라요 ("OCT 13 – 17"): core I18nDates.
        val range = I18nDates.range(monday.monthValue, monday.dayOfMonth, friday.monthValue, friday.dayOfMonth)
        return if (monday.year != today.year) I18nDates.withYear(monday.year, range) else range
    }

    private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

    /** 고른 날의 큰 제목: "10월 14일(화)", 오늘이면 "오늘 · 10월 14일(화)", 다른 해면 앞에 연도. */
    fun dayTitle(d: LocalDate, today: LocalDate): String {
        val md = I18nDates.monthDay(d.monthValue, d.dayOfMonth)
        val day = I18nDates.withWeekday(if (d.year != today.year) I18nDates.withYear(d.year, md) else md, WEEKDAYS[d.dayOfWeek.value - 1])
        return if (d == today) I18nDates.today(day) else day
    }
}
