package kr.co.gcflarchive.core.map

import kr.co.gcflarchive.core.net.parseObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecommendedTest {
    private val body = """
        {"places": [
          {"id": "123", "place_name": "극플카페", "road_address_name": "과천대로 1", "category_name": "음식점 > 카페",
           "x": 126.99, "y": 37.43, "businessType": "카페", "reviewCount": 5, "avgRating": 4.6},
          {"id": "456", "place_name": "분식집", "category_name": "음식점 > 분식", "x": "127.0", "y": "37.42",
           "businessType": "음식점", "reviewCount": 2, "avgRating": null},
          {"id": "bad", "place_name": "좌표 없음", "x": "", "y": ""}
        ],
        "types": [{"type": "카페", "count": 1}, {"type": "음식점", "count": 1}]}
    """.trimIndent()

    @Test
    fun parsesPlacesTypesAndRatings() {
        val idx = RecommendedIndex.parse(parseObject(body))
        assertEquals(listOf("123", "456"), idx.places.map { it.place.id })
        assertEquals(126.99, idx.places[0].place.x)
        assertEquals(4.6, idx.places[0].avgRating)
        assertNull(idx.places[1].avgRating)
        assertEquals(listOf("카페" to 1, "음식점" to 1), idx.types)
        assertEquals("카페", idx.typeOf("123"))
    }

    @Test
    fun colorsMatchTheWeb() {
        assertEquals(0xFF8D5A2B, BusinessTypes.color("카페"))
        assertEquals(0xFF6B7280, BusinessTypes.color("없는 업종"))
    }
}
