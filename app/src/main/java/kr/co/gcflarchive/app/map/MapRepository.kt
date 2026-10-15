package kr.co.gcflarchive.app.map

import kr.co.gcflarchive.app.data.SiteApi
import org.json.JSONArray
import org.json.JSONObject

data class MapUser(val verified: Boolean, val nickname: String)

/** The one nickname shown on 지도 후기 and 커뮤니티 (/api/nickname). [nextChangeAt]: ISO time, blank when it can change now. */
data class NicknameStatus(val nickname: String, val canChange: Boolean, val nextChangeAt: String, val changeDays: Int)

/** /api/map/… endpoints (routes/map_api.py). */
/** [limited]: the server couldn't reach Kakao, so only places with reviews were searched. */
data class PlaceSearch(val places: List<Place>, val limited: Boolean)

object MapRepository {
    suspend fun searchPlaces(query: String): PlaceSearch {
        val j = JSONObject(SiteApi.get("/api/map/search?q=" + android.net.Uri.encode(query)))
        return PlaceSearch(MapLogic.parsePlaces(j.optJSONArray("places")), j.optBoolean("limited"))
    }

    suspend fun search(query: String): List<Place> = searchPlaces(query).places

    suspend fun customPlaces(): List<Place> {
        val arr = JSONArray(SiteApi.get("/api/map/custom-places"))
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(MapLogic::parseCustomPlace) }
    }

    /** 극플인의 추천: places with reviews, with 업종 / review count / ★ average. */
    suspend fun recommended(): RecommendedIndex = RecommendedIndex.parse(JSONObject(SiteApi.get("/api/map/reviewed-places")))

    suspend fun stats(): Map<String, PlaceStats> = MapLogic.parseStats(JSONObject(SiteApi.get("/api/map/places-stats")))

    suspend fun user(): MapUser {
        val j = JSONObject(SiteApi.get("/api/map/user-status"))
        return MapUser(j.optBoolean("verified"), j.optString("nickname"))
    }

    suspend fun nickname(): NicknameStatus {
        val j = JSONObject(SiteApi.get("/api/nickname"))
        return NicknameStatus(j.optString("nickname"), j.optBoolean("canChange", true), j.optString("nextChangeAt"), j.optInt("changeDays", 7))
    }

    /** Sets or changes the shared nickname; earlier 후기·글·댓글 follow the change on the server. */
    suspend fun setNickname(name: String) {
        val res = SiteApi.exchange("/api/nickname", "PUT", JSONObject().put("nickname", name.trim()))
        if (res.code == 401) throw kr.co.gcflarchive.app.data.LoginRequiredException(res.error ?: "로그인하면 닉네임을 정할 수 있어요.")
        if (!res.isSuccessful) throw kr.co.gcflarchive.app.data.ApiException(res.code, res.error ?: "닉네임을 저장하지 못했어요. 잠시 후 다시 시도해주세요.")
    }

    /** First nickname (지도·커뮤니티): same endpoint as changing it in 설정. */
    suspend fun registerNickname(name: String) = setNickname(name)

    suspend fun favorites(): List<Place> = MapLogic.parsePlaces(JSONObject(SiteApi.get("/api/map/favorites")).optJSONArray("favorites"))

    /** Returns whether the place is now starred. */
    suspend fun toggleFavorite(place: Place): Boolean =
        JSONObject(SiteApi.postJson("/api/map/favorites", JSONObject().put("placeId", place.id).put("place", place.toJson()))).optBoolean("starred")

    suspend fun view(placeId: String): Int =
        JSONObject(SiteApi.postJson("/api/map/places/${android.net.Uri.encode(placeId)}/view", JSONObject())).optInt("views")

    suspend fun reviews(placeId: String): List<Review> =
        MapLogic.parseReviews(JSONArray(SiteApi.get("/api/map/reviews?placeId=" + android.net.Uri.encode(placeId))))

    suspend fun postReview(place: Place, rating: Int, content: String, hashtags: String, anonymous: Boolean) {
        SiteApi.postJson(
            "/api/map/reviews",
            JSONObject().put("placeId", place.id).put("placeName", place.name).put("placeCategory", place.category)
                .put("placeAddress", place.displayAddress).put("placeX", place.x).put("placeY", place.y)
                .put("isAnonymous", anonymous).put("rating", rating).put("content", content).put("hashtags", hashtags),
        )
    }

    suspend fun suggestSchedule(place: Place, schedule: Map<String, DaySchedule>) {
        SiteApi.postJson(
            "/api/map/place-details",
            JSONObject().put("placeId", place.id).put("placeName", place.name).put("schedule", MapLogic.scheduleJson(schedule)),
        )
    }

    /** Road address → (x, y) via the server's Kakao proxy. */
    suspend fun geocode(address: String): Pair<Double, Double> {
        val j = JSONObject(SiteApi.get("/api/map/geocode?address=" + android.net.Uri.encode(address)))
        return j.getDouble("x") to j.getDouble("y")
    }

    suspend fun registerPlace(name: String, road: String, floor: String, other: String, x: Double, y: Double) {
        SiteApi.postJson(
            "/api/map/custom-places",
            JSONObject().put("placeName", name).put("roadAddress", road).put("floor", floor).put("otherInfo", other)
                .put("placeX", x).put("placeY", y),
        )
    }
}
