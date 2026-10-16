package kr.co.gcflarchive.core.i18n

import kr.co.gcflarchive.core.school.ClassRef
import kr.co.gcflarchive.core.school.teacherLabel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class I18nTest {
    @AfterTest
    fun back() = I18n.useForTest(I18n.DEFAULT, emptyMap())

    @Test
    fun koreanIsTheKeyAndTheFallback() {
        I18n.useForTest(I18n.DEFAULT, emptyMap())
        assertEquals("보강 부탁", I18n.tr("보강 부탁"))
        assertEquals("취소", I18n.tr("취소|상태"))
        assertEquals("김과천 선생님께 전달했어요.", I18n.tr("{name} 선생님께 전달했어요.", "name" to "김과천"))
        assertEquals("이외고T(영)", teacherLabel("이외고(영)"))
        assertEquals("2학년 3반", ClassRef(2, 3).label)
    }

    @Test
    fun englishStringsWithPlaceholders() {
        var changes = 0
        I18n.onChange = { changes++ }
        I18n.useForTest("en", mapOf("보강 부탁" to "Cover requests", "취소|상태" to "Cancelled", "{g}학년 {k}반" to "Grade {g} Class {k}",
            "{name} 선생님께 전달했어요." to "Forwarded to {name}."))
        assertEquals(1, changes)
        assertEquals("Cover requests", I18n.tr("보강 부탁"))
        assertEquals("Cancelled", I18n.tr("취소|상태"))
        assertEquals("번역 없음", I18n.tr("번역 없음"))
        assertEquals("Forwarded to 김과천.", I18n.tr("{name} 선생님께 전달했어요.", "name" to "김과천"))
        assertEquals("Grade 2 Class 3", ClassRef(2, 3).label)
        assertEquals("이외고(영)", teacherLabel("이외고(영)"))  // no 'T' outside 한국어
        I18n.onChange = null
    }
}

class I18nDatesTest {
    @AfterTest
    fun back() = I18n.useForTest(I18n.DEFAULT, emptyMap())

    private val en = mapOf(
        "{m}월 {d}일" to "{MON} {d}", "{d}일" to "{d}", "{y}년 {when}" to "{when}, {y}",
        "{date}({wd})" to "{wd}, {date}", "오늘 · {day}" to "Today · {day}", "수" to "Wed",
    )

    @Test
    fun koreanUnchanged() {
        I18n.useForTest(I18n.DEFAULT, emptyMap())
        val today = kotlinx.datetime.LocalDate(2026, 10, 14)
        assertEquals("10월 12일 – 16일", kr.co.gcflarchive.core.meal.MealWeek.label(kotlinx.datetime.LocalDate(2026, 10, 12), today))
        assertEquals("9월 28일 – 10월 2일", kr.co.gcflarchive.core.meal.MealWeek.label(kotlinx.datetime.LocalDate(2026, 9, 28), today))
        assertEquals("2027년 1월 4일 – 8일", kr.co.gcflarchive.core.meal.MealWeek.label(kotlinx.datetime.LocalDate(2027, 1, 4), today))
        assertEquals("오늘 · 10월 14일(수)", kr.co.gcflarchive.core.meal.MealWeek.dayTitle(today, today))
    }

    @Test
    fun englishUsesThreeLetterMonths() {
        I18n.useForTest("en", en)
        val today = kotlinx.datetime.LocalDate(2026, 10, 14)
        assertEquals("OCT 12 – 16", kr.co.gcflarchive.core.meal.MealWeek.label(kotlinx.datetime.LocalDate(2026, 10, 12), today))
        assertEquals("SEP 28 – OCT 2", kr.co.gcflarchive.core.meal.MealWeek.label(kotlinx.datetime.LocalDate(2026, 9, 28), today))
        assertEquals("JAN 4 – 8, 2027", kr.co.gcflarchive.core.meal.MealWeek.label(kotlinx.datetime.LocalDate(2027, 1, 4), today))
        assertEquals("Today · Wed, OCT 14", kr.co.gcflarchive.core.meal.MealWeek.dayTitle(today, today))
    }
}
