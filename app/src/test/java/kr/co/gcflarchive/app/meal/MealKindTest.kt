package kr.co.gcflarchive.app.meal

import org.junit.Assert.assertEquals
import org.junit.Test

class MealKindTest {
    private val breakfast = Meal("조식", null, emptyList())
    private val lunch = Meal("중식", null, emptyList())
    private val dinner = Meal("석식", null, emptyList())

    @Test
    fun noneOneOrBoth() {
        val all = listOf(breakfast, lunch, dinner)
        assertEquals(listOf(lunch, dinner), MealKind.filter(all, MealKind.DEFAULT))
        assertEquals(listOf(lunch), MealKind.filter(all, setOf(MealKind.LUNCH)))
        assertEquals(emptyList<Meal>(), MealKind.filter(all, emptySet()))
        assertEquals("점심·저녁", MealKind.label(setOf(MealKind.DINNER, MealKind.LUNCH)))
        assertEquals("선택 안 함", MealKind.label(emptySet()))
    }

    @Test
    fun encodingAndLegacy() {
        assertEquals("LUNCH,DINNER", MealKind.encode(setOf(MealKind.DINNER, MealKind.LUNCH)))
        assertEquals(emptySet<MealKind>(), MealKind.decode(""))
        assertEquals(null, MealKind.decode(null))
        assertEquals(setOf(MealKind.DINNER), MealKind.decode("DINNER"))
        assertEquals(MealKind.DEFAULT, MealKind.fromLegacy(0)) // 전체
        assertEquals(MealKind.DEFAULT, MealKind.fromLegacy(1)) // 조식 (removed)
        assertEquals(setOf(MealKind.LUNCH), MealKind.fromLegacy(2))
        assertEquals(setOf(MealKind.DINNER), MealKind.fromLegacy(3))
    }
}
