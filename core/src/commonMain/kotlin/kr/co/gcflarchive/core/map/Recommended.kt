package kr.co.gcflarchive.core.map

import kr.co.gcflarchive.core.net.JObject
import kr.co.gcflarchive.core.net.objects

/** 추천 업소: a place with reviews (GET /api/map/reviewed-places), with its 업종. */
data class RecommendedPlace(val place: Place, val businessType: String, val reviewCount: Int, val avgRating: Double?)

data class RecommendedIndex(val places: List<RecommendedPlace>, val types: List<Pair<String, Int>>) {
    fun typeOf(placeId: String): String? = places.firstOrNull { it.place.id == placeId }?.businessType

    companion object {
        val EMPTY = RecommendedIndex(emptyList(), emptyList())

        fun parse(json: JObject): RecommendedIndex {
            val places = json.optJSONArray("places").objects().mapNotNull { o ->
                val place = MapLogic.parsePlace(o) ?: return@mapNotNull null
                RecommendedPlace(
                    place = place,
                    businessType = o.optString("businessType").ifBlank { BusinessTypes.OTHER },
                    reviewCount = o.optInt("reviewCount"),
                    avgRating = if (o.isNull("avgRating")) null else o.optDouble("avgRating").takeIf { !it.isNaN() && it > 0 },
                )
            }
            val types = json.optJSONArray("types").objects().mapNotNull { o ->
                o.optString("type").takeIf { it.isNotBlank() }?.let { it to o.optInt("count") }
            }
            return RecommendedIndex(places, types)
        }
    }
}

/** 업종 colors (0xAARRGGBB), same as map.html BUSINESS_TYPE_COLORS. */
object BusinessTypes {
    const val OTHER = "기타"

    private val COLORS = mapOf(
        "음식점" to 0xFFE5484D,
        "카페" to 0xFF8D5A2B,
        "학원" to 0xFF3B6FD8,
        "편의점·마트" to 0xFF1F9D55,
        "병원·약국" to 0xFFD6409F,
        "문화·여가" to 0xFF8E4EC6,
        "미용·생활" to 0xFFE08A00,
        OTHER to 0xFF6B7280,
    )

    fun color(type: String): Long = COLORS[type] ?: COLORS.getValue(OTHER)
}
