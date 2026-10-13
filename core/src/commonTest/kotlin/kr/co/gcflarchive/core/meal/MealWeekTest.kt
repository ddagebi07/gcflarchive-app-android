package kr.co.gcflarchive.core.meal

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MealWeekTest {
    private val wed = LocalDate(2026, 10, 14)

    @Test
    fun weekendSnapsToNextMonday() {
        assertEquals(LocalDate(2026, 10, 19), MealWeek.schoolDay(LocalDate(2026, 10, 17)))
        assertEquals(LocalDate(2026, 10, 19), MealWeek.schoolDay(LocalDate(2026, 10, 18)))
        assertEquals(wed, MealWeek.schoolDay(wed))
    }

    @Test
    fun weekDaysAndShift() {
        assertEquals(LocalDate(2026, 10, 12), MealWeek.monday(wed))
        assertEquals((12..16).map { LocalDate(2026, 10, it) }, MealWeek.days(MealWeek.monday(wed)))
        assertEquals(LocalDate(2026, 10, 21), MealWeek.plusWeeks(wed, 1))
        assertEquals(-2, MealWeek.weekIndex(wed, LocalDate(2026, 9, 30)))
        assertEquals(1, MealWeek.weekIndex(LocalDate(2026, 10, 16), LocalDate(2026, 10, 19)))
    }

    @Test
    fun labels() {
        assertEquals("10월 12일 – 16일", MealWeek.label(LocalDate(2026, 10, 12), wed))
        assertEquals("9월 28일 – 10월 2일", MealWeek.label(LocalDate(2026, 9, 28), wed))
        assertEquals("2027년 1월 4일 – 8일", MealWeek.label(LocalDate(2027, 1, 4), wed))
    }
}
