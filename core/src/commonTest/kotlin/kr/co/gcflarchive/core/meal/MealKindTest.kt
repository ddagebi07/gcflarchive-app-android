package kr.co.gcflarchive.core.meal

import com.russhwolf.settings.MapSettings
import kr.co.gcflarchive.core.prefs.AppPrefs
import kotlin.test.Test
import kotlin.test.assertEquals

class MealKindTest {
    private val lunch = Meal("중식", null, emptyList())
    private val dinner = Meal("석식", null, emptyList())
    private val breakfast = Meal("조식", null, emptyList())

    @Test
    fun anyCombinationIncludingNone() {
        val all = listOf(breakfast, lunch, dinner)
        assertEquals(listOf(lunch, dinner), MealRepository.forKinds(all, MealKind.DEFAULT))
        assertEquals(listOf(dinner), MealRepository.forKinds(all, setOf(MealKind.DINNER)))
        assertEquals(emptyList(), MealRepository.forKinds(all, emptySet()))
        assertEquals("점심·저녁", MealKind.label(setOf(MealKind.DINNER, MealKind.LUNCH)))
        assertEquals("고르지 않음", MealKind.label(emptySet()))
    }

    @Test
    fun storedAndMigrated() {
        val settings = MapSettings()
        val prefs = AppPrefs(settings)
        assertEquals(MealKind.DEFAULT, prefs.mealNotifyKinds)
        settings.putInt("meal_notify_kind", 3) // old "석식"
        assertEquals(setOf(MealKind.DINNER), prefs.mealNotifyKinds)
        settings.putInt("meal_notify_kind", 1) // old "조식" → 점심·저녁
        assertEquals(MealKind.DEFAULT, prefs.mealNotifyKinds)
        prefs.mealNotifyKinds = emptySet()
        assertEquals(emptySet(), prefs.mealNotifyKinds)
        prefs.mealNotifyKinds = setOf(MealKind.LUNCH)
        assertEquals(setOf(MealKind.LUNCH), prefs.mealNotifyKinds)
    }
}
