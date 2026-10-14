package kr.co.gcflarchive.core.map

import kr.co.gcflarchive.core.encPath
import kr.co.gcflarchive.core.encQuery
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.jsonOf
import kr.co.gcflarchive.core.net.objects
import kr.co.gcflarchive.core.net.parseArray

data class MapUser(val verified: Boolean, val nickname: String)

/** The one nickname shown on 지도 후기 and 커뮤니티 (/api/nickname). [nextChangeAt]: ISO time, blank when it can change now. */
data class NicknameStatus(val nickname: String, val canChange: Boolean, val nextChangeAt: String, val changeDays: Int)

/** /api/map/… (routes/map_api.py). */
class MapRepository(private val site: SiteClient) {
    suspend fun search(query: String): List<Place> =
        MapLogic.parsePlaces(site.getJson("/api/map/search?q=" + encQuery(query)).optJSONArray("places"))

    suspend fun customPlaces(): List<Place> = parseArray(site.get("/api/map/custom-places")).objects().mapNotNull(MapLogic::parseCustomPlace)

    /** 극플인의 추천: places with reviews, with 업종 / review count / ★ average. */
    suspend fun recommended(): RecommendedIndex = RecommendedIndex.parse(site.getJson("/api/map/reviewed-places"))

    suspend fun stats(): Map<String, PlaceStats> = MapLogic.parseStats(site.getJson("/api/map/places-stats"))

    suspend fun user(): MapUser {
        val j = site.getJson("/api/map/user-status")
        return MapUser(j.optBoolean("verified"), j.optString("nickname"))
    }

    suspend fun nickname(): NicknameStatus {
        val j = site.getJson("/api/nickname")
        return NicknameStatus(j.optString("nickname"), j.optBoolean("canChange", true), j.optString("nextChangeAt"), j.optInt("changeDays", 7))
    }

    /** Sets or changes the shared nickname; earlier 후기·글·댓글 follow the change on the server. */
    suspend fun setNickname(name: String) {
        val res = site.exchange("/api/nickname", "PUT", jsonOf("nickname" to name.trim()))
        if (res.code == 401) throw kr.co.gcflarchive.core.net.LoginRequiredException(res.error ?: "로그인하면 닉네임을 정할 수 있어요.")
        if (!res.isSuccessful) throw kr.co.gcflarchive.core.net.ApiException(res.code, res.error ?: "닉네임을 저장하지 못했어요. 잠시 후 다시 시도해주세요.")
    }

    /** First nickname (지도·커뮤니티): same endpoint as changing it in 설정. */
    suspend fun registerNickname(name: String) = setNickname(name)

    suspend fun favorites(): List<Place> = MapLogic.parsePlaces(site.getJson("/api/map/favorites").optJSONArray("favorites"))

    /** Returns whether the place is now starred. */
    suspend fun toggleFavorite(place: Place): Boolean =
        site.postForJson("/api/map/favorites", jsonOf("placeId" to place.id, "place" to place.toJson())).optBoolean("starred")

    suspend fun view(placeId: String): Int = site.postForJson("/api/map/places/${encPath(placeId)}/view").optInt("views")

    suspend fun reviews(placeId: String): List<Review> =
        MapLogic.parseReviews(parseArray(site.get("/api/map/reviews?placeId=" + encQuery(placeId))))

    suspend fun postReview(place: Place, rating: Int, content: String, hashtags: String, anonymous: Boolean) {
        site.postJson(
            "/api/map/reviews",
            jsonOf(
                "placeId" to place.id, "placeName" to place.name, "placeCategory" to place.category,
                "placeAddress" to place.displayAddress, "placeX" to place.x, "placeY" to place.y,
                "isAnonymous" to anonymous, "rating" to rating, "content" to content, "hashtags" to hashtags,
            ),
        )
    }

    suspend fun suggestSchedule(place: Place, schedule: Map<String, DaySchedule>) {
        site.postJson("/api/map/place-details", jsonOf("placeId" to place.id, "placeName" to place.name, "schedule" to MapLogic.scheduleJson(schedule)))
    }

    /** Road address → (x, y) via the server's Kakao proxy. */
    suspend fun geocode(address: String): Pair<Double, Double> {
        val j = site.getJson("/api/map/geocode?address=" + encQuery(address))
        return j.optDouble("x") to j.optDouble("y")
    }

    suspend fun registerPlace(name: String, road: String, floor: String, other: String, x: Double, y: Double) {
        site.postJson(
            "/api/map/custom-places",
            jsonOf("placeName" to name, "roadAddress" to road, "floor" to floor, "otherInfo" to other, "placeX" to x, "placeY" to y),
        )
    }
}
