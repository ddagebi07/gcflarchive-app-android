package kr.co.gcflarchive.app.meal

/** Meals the 급식 알림 can include; any of 0–2 may be chosen (조식 is not offered). */
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

        /** "점심·저녁", "점심", "선택 안 함" */
        fun label(kinds: Set<MealKind>): String = entries.filter { it in kinds }.joinToString("·") { it.label }.ifEmpty { "고르지 않음" }

        fun filter(meals: List<Meal>, kinds: Set<MealKind>): List<Meal> = meals.filter { m -> kinds.any { it.neisName == m.kind } }
    }
}
