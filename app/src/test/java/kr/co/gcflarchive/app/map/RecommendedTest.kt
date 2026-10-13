package kr.co.gcflarchive.app.map

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        val idx = RecommendedIndex.parse(JSONObject(body))
        assertEquals(listOf("123", "456"), idx.places.map { it.place.id })
        assertEquals(126.99, idx.places[0].place.x, 1e-9)
        assertEquals(4.6, idx.places[0].avgRating!!, 1e-9)
        assertNull(idx.places[1].avgRating)
        assertEquals(listOf("카페" to 1, "음식점" to 1), idx.types)
        assertEquals("카페", idx.typeOf("123"))
        assertNull(idx.typeOf("999"))
    }

    @Test
    fun colorsMatchTheWebAndFallBack() {
        assertEquals(0xFF8D5A2B.toInt(), BusinessTypes.color("카페"))
        assertEquals(0xFF6B7280.toInt(), BusinessTypes.color("없는 업종"))
    }
}
