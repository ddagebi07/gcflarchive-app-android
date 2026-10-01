package kr.co.gcflarchive.app.map

import org.json.JSONArray
import org.json.JSONObject

/** A place as map.html passes it around (Kakao Local shape, plus our custom places). */
data class Place(
    val id: String,
    val name: String,
    val roadAddress: String,
    val address: String,
    val category: String,
    val categoryGroup: String,
    val x: Double,
    val y: Double,
    val isCustom: Boolean,
) {
    val displayAddress: String get() = roadAddress.ifBlank { address }

    /** Body for POST /api/map/favorites (same keys the web stores). */
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("place_name", name)
        .put("road_address_name", displayAddress).put("address_name", address.ifBlank { roadAddress })
        .put("category_name", category).put("category_group_name", categoryGroup)
        .put("x", x).put("y", y).put("isCustom", isCustom)
}

data class DaySchedule(val open: String, val close: String, val breakStart: String, val breakEnd: String, val holiday: Boolean)

data class PlaceStats(val reviewCount: Int, val views: Int, val hashtags: List<String>, val schedule: Map<String, DaySchedule>?)

data class Review(
    val author: String,
    val rating: Int,
    val content: String,
    val hashtags: List<String>,
    val timestamp: String,
)

object MapLogic {
    /** Order and Korean labels of the weekly schedule keys. */
    val DAYS = listOf("mon" to "월", "tue" to "화", "wed" to "수", "thu" to "목", "fri" to "금", "sat" to "토", "sun" to "일")

    val RATING_LABELS = listOf("별점 선택 안 함", "최악이에요 😞", "그냥 그래요 😐", "괜찮아요 🙂", "맛있어요/좋아요 😋", "최고의 명소에요! 😍")

    /** Search results, favorites: Kakao-shaped objects. */
    fun parsePlace(o: JSONObject): Place? {
        val id = o.optString("id").ifBlank { o.optString("placeId") }
        val x = o.optString("x").toDoubleOrNull() ?: return null
        val y = o.optString("y").toDoubleOrNull() ?: return null
        if (id.isBlank()) return null
        return Place(
            id = id,
            name = o.optString("place_name").ifBlank { o.optString("placeName") },
            roadAddress = o.optString("road_address_name"),
            address = o.optString("address_name"),
            category = o.optString("category_name").ifBlank { "기타" },
            categoryGroup = o.optString("category_group_name"),
            x = x,
            y = y,
            isCustom = o.optBoolean("isCustom") || id.startsWith("cp_"),
        )
    }

    fun parsePlaces(arr: JSONArray?): List<Place> = (0 until (arr?.length() ?: 0)).mapNotNull { arr!!.optJSONObject(it)?.let(::parsePlace) }

    /** /api/map/custom-places entries (approved only). */
    fun parseCustomPlace(o: JSONObject): Place? {
        val floor = o.optString("floor").trim()
        return Place(
            id = o.optString("placeId").ifBlank { return null },
            name = o.optString("placeName") + if (floor.isNotEmpty()) " ($floor)" else "",
            roadAddress = o.optString("roadAddress"),
            address = o.optString("roadAddress"),
            category = o.optString("otherInfo").ifBlank { "직접 등록한 장소" },
            categoryGroup = "직접 등록",
            x = o.optDouble("x").takeIf { !it.isNaN() } ?: return null,
            y = o.optDouble("y").takeIf { !it.isNaN() } ?: return null,
            isCustom = true,
        )
    }

    fun parseStats(o: JSONObject): Map<String, PlaceStats> = o.keys().asSequence().mapNotNull { id ->
        val s = o.optJSONObject(id) ?: return@mapNotNull null
        id to PlaceStats(
            reviewCount = s.optInt("reviewCount"),
            views = s.optInt("views"),
            hashtags = s.optJSONArray("hashtags")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            schedule = s.optJSONObject("schedule")?.let(::parseSchedule),
        )
    }.toMap()

    fun parseSchedule(o: JSONObject): Map<String, DaySchedule> = DAYS.mapNotNull { (key, _) ->
        val d = o.optJSONObject(key) ?: return@mapNotNull null
        key to DaySchedule(d.optString("open"), d.optString("close"), d.optString("breakStart"), d.optString("breakEnd"), d.optBoolean("isHoliday"))
    }.toMap()

    fun scheduleJson(schedule: Map<String, DaySchedule>): JSONObject = JSONObject().apply {
        for ((key, d) in schedule) {
            put(key, JSONObject().put("open", if (d.holiday) "" else d.open).put("close", if (d.holiday) "" else d.close)
                .put("breakStart", if (d.holiday) "" else d.breakStart).put("breakEnd", if (d.holiday) "" else d.breakEnd)
                .put("isHoliday", d.holiday))
        }
    }

    /** "09:00 ~ 21:00 (휴게시간: 15:00 ~ 17:00)" or "휴무일". */
    fun describe(d: DaySchedule): String = if (d.holiday) "휴무일" else buildString {
        append("${d.open} ~ ${d.close}")
        if (d.breakStart.isNotBlank() && d.breakEnd.isNotBlank()) append(" (휴게시간: ${d.breakStart} ~ ${d.breakEnd})")
    }

    fun isValidTime(t: String): Boolean = t.isEmpty() || Regex("""([01]\d|2[0-3]):[0-5]\d""").matches(t)

    fun parseReviews(arr: JSONArray): List<Review> = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { r ->
        val anonymous = r.optBoolean("isAnonymous")
        Review(
            author = if (anonymous) "익명" else r.optString("nickname").ifBlank { "재학생" } + r.optString("userKey").let { if (it.isBlank()) "" else " ($it)" },
            rating = r.optInt("rating").coerceIn(0, 5),
            content = r.optString("content"),
            hashtags = r.optJSONArray("hashtags")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            timestamp = r.optString("timestamp"),
        )
    }

    /** Nickname rule from /api/map/register-nickname; null when valid. */
    fun nicknameError(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "닉네임을 입력해 주세요."
            n.length !in 2..15 -> "닉네임은 2자 이상 15자 이하여야 합니다."
            !Regex("""^[a-zA-Z0-9가-힣\s_]+$""").matches(n) -> "닉네임에는 한글, 영문, 숫자, 밑줄, 공백만 사용 가능합니다."
            else -> null
        }
    }

    /** Link the web uses for 공유 (/map?placeId=…&placeName=…). */
    fun shareUrl(base: String, place: Place): String =
        "$base/map?placeId=${place.id}&placeName=${place.name.replace(" ", "%20")}"
}
