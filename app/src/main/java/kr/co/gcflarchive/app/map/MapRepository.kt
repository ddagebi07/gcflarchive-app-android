package kr.co.gcflarchive.app.map

import kr.co.gcflarchive.app.data.SiteApi
import org.json.JSONArray
import org.json.JSONObject

data class MapUser(val verified: Boolean, val nickname: String)

/** /api/map/* (routes/map_api.py). */
object MapRepository {
    suspend fun search(query: String): List<Place> =
        MapLogic.parsePlaces(JSONObject(SiteApi.get("/api/map/search?q=" + android.net.Uri.encode(query))).optJSONArray("places"))

    suspend fun customPlaces(): List<Place> {
        val arr = JSONArray(SiteApi.get("/api/map/custom-places"))
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(MapLogic::parseCustomPlace) }
    }

    suspend fun stats(): Map<String, PlaceStats> = MapLogic.parseStats(JSONObject(SiteApi.get("/api/map/places-stats")))

    suspend fun user(): MapUser {
        val j = JSONObject(SiteApi.get("/api/map/user-status"))
        return MapUser(j.optBoolean("verified"), j.optString("nickname"))
    }

    suspend fun registerNickname(name: String) {
        SiteApi.postJson("/api/map/register-nickname", JSONObject().put("nickname", name.trim()))
    }

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
