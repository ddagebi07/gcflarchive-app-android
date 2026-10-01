package kr.co.gcflarchive.app.meal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.data.Http
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Dish(val name: String, val allergens: String?)

data class Meal(
    val kind: String,       // 조식 / 중식 / 석식
    val calories: String?,
    val dishes: List<Dish>,
)

object MealRepository {
    val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    val NEIS_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val ALLERGY = Regex("""\(([0-9.]+)\)""")

    fun today(): LocalDate = LocalDate.now(SEOUL)

    /** Empty list means no meal service that day (weekend, holiday, vacation). */
    suspend fun fetch(date: LocalDate): List<Meal> = withContext(Dispatchers.IO) {
        val url = "https://open.neis.go.kr/hub/mealServiceDietInfo".toHttpUrl().newBuilder()
            .addQueryParameter("Type", "json")
            .addQueryParameter("pIndex", "1")
            .addQueryParameter("pSize", "10")
            .addQueryParameter("ATPT_OFCDC_SC_CODE", Config.NEIS_OFFICE_CODE)
            .addQueryParameter("SD_SCHUL_CODE", Config.NEIS_SCHOOL_CODE)
            .addQueryParameter("MLSV_YMD", date.format(NEIS_DATE))
            .addQueryParameter("KEY", Config.NEIS_API_KEY)
            .build()
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("NEIS HTTP ${res.code}")
            parse(res.body?.string().orEmpty())
        }
    }

    internal fun parse(body: String): List<Meal> {
        val json = JSONObject(body)
        // No data comes back as {"RESULT":{"CODE":"INFO-200",...}}.
        val info = json.optJSONArray("mealServiceDietInfo") ?: return emptyList()
        val rows = (0 until info.length())
            .mapNotNull { info.optJSONObject(it)?.optJSONArray("row") }
            .firstOrNull() ?: return emptyList()
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val dishes = row.optString("DDISH_NM").split("<br/>").mapNotNull { raw ->
                val name = ALLERGY.replace(raw, "").trim()
                if (name.isEmpty()) null else Dish(name, ALLERGY.find(raw)?.groupValues?.get(1))
            }
            Meal(
                kind = row.optString("MMEAL_SC_NM").ifBlank { "급식" },
                calories = row.optString("CAL_INFO").ifBlank { null },
                dishes = dishes,
            )
        }
    }
}
