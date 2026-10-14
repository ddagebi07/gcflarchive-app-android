package kr.co.gcflarchive.core.meal

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kr.co.gcflarchive.core.Config
import kr.co.gcflarchive.core.net.ApiException
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.parseObject

data class Dish(val name: String, val allergens: String?)

data class Meal(
    val kind: String, // 조식 / 중식 / 석식
    val calories: String?,
    val dishes: List<Dish>,
)

/** Meals the 급식 알림 can include (조식 is not offered). */
enum class MealKind(val neisName: String, val label: String) {
    LUNCH("중식", "점심"), DINNER("석식", "저녁");

    companion object {
        val DEFAULT: Set<MealKind> = setOf(LUNCH, DINNER)

        /** "LUNCH,DINNER" ↔ set; "" is a valid empty choice (no 급식 알림), null = never saved. */
        fun decode(raw: String?): Set<MealKind>? =
            raw?.split(',')?.mapNotNull { n -> entries.firstOrNull { it.name == n.trim() } }?.toSet()

        fun encode(kinds: Set<MealKind>): String = entries.filter { it in kinds }.joinToString(",") { it.name }

        /** The old single choice (0 전체, 1 조식, 2 중식, 3 석식). */
        fun fromLegacy(ordinal: Int): Set<MealKind> = when (ordinal) {
            2 -> setOf(LUNCH)
            3 -> setOf(DINNER)
            else -> DEFAULT
        }

        /** "점심·저녁", "점심", "고르지 않음" */
        fun label(kinds: Set<MealKind>): String = entries.filter { it in kinds }.joinToString("·") { it.label }.ifEmpty { "고르지 않음" }
    }
}

/** School meals from the NEIS open API (same key and school code as meal.html). */
class MealRepository(private val site: SiteClient) {
    /** Empty list means no meal service that day (weekend, holiday, vacation). */
    suspend fun fetch(date: LocalDate): List<Meal> {
        val res = site.http.get("https://open.neis.go.kr/hub/mealServiceDietInfo") {
            parameter("Type", "json")
            parameter("pIndex", "1")
            parameter("pSize", "10")
            parameter("ATPT_OFCDC_SC_CODE", Config.NEIS_OFFICE_CODE)
            parameter("SD_SCHUL_CODE", Config.NEIS_SCHOOL_CODE)
            parameter("MLSV_YMD", neisDate(date))
            parameter("KEY", Config.NEIS_API_KEY)
        }
        if (res.status.value !in 200..299) throw ApiException(res.status.value, "NEIS HTTP ${res.status.value}")
        return parse(res.bodyAsText())
    }

    companion object {
        val SEOUL: TimeZone = TimeZone.of("Asia/Seoul")
        private val ALLERGY = Regex("""\(([0-9.]+)\)""")

        fun today(): LocalDate = Clock.System.now().toLocalDateTime(SEOUL).date
        fun nowHour(): Int = Clock.System.now().toLocalDateTime(SEOUL).hour

        fun neisDate(d: LocalDate): String =
            "${d.year}${d.monthNumber.toString().padStart(2, '0')}${d.dayOfMonth.toString().padStart(2, '0')}"

        fun parse(body: String): List<Meal> {
            // No data comes back as {"RESULT":{"CODE":"INFO-200",...}}.
            val info = parseObject(body).optJSONArray("mealServiceDietInfo") ?: return emptyList()
            val rows = info.objects().firstNotNullOfOrNull { it.optJSONArray("row") } ?: return emptyList()
            return rows.objects().map { row ->
                val dishes = row.optString("DDISH_NM").split("<br/>").mapNotNull { raw ->
                    val name = ALLERGY.replace(raw, "").trim()
                    if (name.isEmpty()) null else Dish(name, ALLERGY.find(raw)?.groupValues?.get(1))
                }
                Meal(row.optString("MMEAL_SC_NM").ifBlank { "급식" }, row.optString("CAL_INFO").ifBlank { null }, dishes)
            }
        }

        /** The meal worth showing on the home card: lunch, or dinner from 14:00. */
        fun featured(meals: List<Meal>, hour: Int): Meal? {
            val preferred = if (hour >= 14) "석식" else "중식"
            return meals.firstOrNull { it.kind == preferred } ?: meals.firstOrNull()
        }

        /** Meals matching the notification preference (조식 never included). */
        fun forKinds(meals: List<Meal>, kinds: Set<MealKind>): List<Meal> =
            meals.filter { m -> kinds.any { it.neisName == m.kind } }

        /** "10월 13일 (월)" */
        fun label(d: LocalDate): String {
            val dow = listOf("월", "화", "수", "목", "금", "토", "일")[d.dayOfWeek.ordinal]
            return "${d.monthNumber}월 ${d.dayOfMonth}일 ($dow)"
        }

        fun shift(d: LocalDate, days: Int): LocalDate = d.plus(DatePeriod(days = days))

        fun isWeekend(d: LocalDate): Boolean = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
    }
}
