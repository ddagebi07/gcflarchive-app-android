package kr.co.gcflarchive.core.i18n

/**
 * 날짜 표기도 번역 파일의 틀(template)을 따라요. 한국어는 그대로 ("10월 12일 – 16일"), 영어는 세 글자 달 약어
 * ("OCT 12 – 16"). 틀에서 {MON}은 영어 달 약어(JAN…DEC), {m}은 달 숫자예요.
 */
object I18nDates {
    val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    /** "10월 12일" / "OCT 12" */
    fun monthDay(month: Int, day: Int): String =
        I18n.tr("{m}월 {d}일", "m" to month, "d" to day, "MON" to MONTHS[(month - 1).coerceIn(0, 11)])

    /** Same month as the start of a range: "16일" / "16" */
    fun dayOnly(day: Int): String = I18n.tr("{d}일", "d" to day)

    /** Another year: "2027년 10월 12일" / "OCT 12, 2027" */
    fun withYear(year: Int, text: String): String = I18n.tr("{y}년 {when}", "y" to year, "when" to text)

    /** "10월 12일 – 16일", "9월 28일 – 10월 2일" / "OCT 12 – 16", "SEP 28 – OCT 2" */
    fun range(m1: Int, d1: Int, m2: Int, d2: Int): String =
        "${monthDay(m1, d1)} – ${if (m1 == m2) dayOnly(d2) else monthDay(m2, d2)}"

    /** "10월 14일(수)" / "Wed, OCT 14" ([weekdayKo]: the Korean one-letter name, translated too). */
    fun withWeekday(date: String, weekdayKo: String): String = I18n.tr("{date}({wd})", "date" to date, "wd" to I18n.tr(weekdayKo))

    /** "오늘 · …" / "Today · …" */
    fun today(text: String): String = I18n.tr("오늘 · {day}", "day" to text)
}
