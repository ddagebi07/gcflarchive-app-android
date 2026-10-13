package kr.co.gcflarchive.app.meal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MealWeekTest {
    private val wed = LocalDate.of(2026, 10, 14)

    @Test
    fun weekendSnapsToNextMonday() {
        assertEquals(LocalDate.of(2026, 10, 19), MealWeek.schoolDay(LocalDate.of(2026, 10, 17)))
        assertEquals(LocalDate.of(2026, 10, 19), MealWeek.schoolDay(LocalDate.of(2026, 10, 18)))
        assertEquals(wed, MealWeek.schoolDay(wed))
    }

    @Test
    fun weekDaysAndShift() {
        assertEquals(LocalDate.of(2026, 10, 12), MealWeek.monday(wed))
        assertEquals((12..16).map { LocalDate.of(2026, 10, it) }, MealWeek.days(MealWeek.monday(wed)))
        assertEquals(LocalDate.of(2026, 10, 21), MealWeek.shiftWeeks(wed, 1))
        assertEquals(LocalDate.of(2026, 10, 7), MealWeek.shiftWeeks(wed, -1))
        assertEquals(-2L, MealWeek.weekIndex(wed, LocalDate.of(2026, 9, 30)))
        assertEquals(1L, MealWeek.weekIndex(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun labels() {
        assertEquals("10월 12일 – 16일", MealWeek.label(LocalDate.of(2026, 10, 12), wed))
        assertEquals("9월 28일 – 10월 2일", MealWeek.label(LocalDate.of(2026, 9, 28), wed))
        assertEquals("2027년 1월 4일 – 8일", MealWeek.label(LocalDate.of(2027, 1, 4), wed))
    }
}
