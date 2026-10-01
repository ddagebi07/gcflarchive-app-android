package kr.co.gcflarchive.app.meal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealRepositoryTest {

    @Test
    fun parsesDishesAndAllergens() {
        val body = """
            {"mealServiceDietInfo":[
              {"head":[{"list_total_count":2},{"RESULT":{"CODE":"INFO-000","MESSAGE":"정상 처리되었습니다."}}]},
              {"row":[
                {"MMEAL_SC_NM":"중식","CAL_INFO":"812.4 Kcal",
                 "DDISH_NM":"흑미밥 <br/>된장찌개 (5.6.9)<br/>제육볶음 (5.6.10.13)<br/>배추김치 (9.13)"},
                {"MMEAL_SC_NM":"석식","CAL_INFO":"","DDISH_NM":"카레라이스 (2.5.6)<br/> "}
              ]}
            ]}
        """.trimIndent()

        val meals = MealRepository.parse(body)

        assertEquals(listOf("중식", "석식"), meals.map { it.kind })
        val lunch = meals[0]
        assertEquals("812.4 Kcal", lunch.calories)
        assertEquals(listOf("흑미밥", "된장찌개", "제육볶음", "배추김치"), lunch.dishes.map { it.name })
        assertNull(lunch.dishes[0].allergens)
        assertEquals("5.6.10.13", lunch.dishes[2].allergens)

        val dinner = meals[1]
        assertNull(dinner.calories)
        assertEquals(listOf(Dish("카레라이스", "2.5.6")), dinner.dishes)
    }

    @Test
    fun noDataMeansNoMeals() {
        val body = """{"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}"""
        assertTrue(MealRepository.parse(body).isEmpty())
    }
}
