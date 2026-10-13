package kr.co.gcflarchive.admin.features.more

import org.json.JSONArray
import org.json.JSONObject

/** 과목 배정 규칙: students of [grade] (blank = 모든 학년) in classes [classStart]..[classEnd] take [subjects]. */
data class SubjectRule(val grade: String, val classStart: String, val classEnd: String, val subjects: List<String>)

/** 선택과목 묶음: one input row where the student picks one of [subjects]. */
data class SelectionGroup(val label: String, val slot: String, val subjects: List<String>)

/**
 * The grade service's exam policy (grade_calculator_backend.py exam_policy.json), edited
 * field by field instead of as raw JSON.
 */
data class ExamPolicy(
    val examName: String,
    val rules: List<SubjectRule>,
    val groups: List<SelectionGroup>,
    /** subject → ISO time from which submission opens. */
    val submitAfter: Map<String, String>,
    val questionCounts: Map<String, Int>,
    val descriptiveCounts: Map<String, Int>,
)

/** Parsing, serialising and validation rules (mirrors the server's normalisers). */
object ExamPolicyCodec {
    val GRADE_OPTIONS = listOf("" to "전체 학년", "1" to "1학년", "2" to "2학년", "3" to "3학년")

    fun parse(policy: JSONObject?): ExamPolicy {
        val p = policy ?: JSONObject()
        val m = p.optJSONObject("subject_mapping") ?: JSONObject()
        val rules = objects(m.optJSONArray("rules")).map {
            SubjectRule(it.optString("grade").trim(), it.optString("class_start").trim(), it.optString("class_end").trim(), strings(it.optJSONArray("subjects")))
        }.filter { it.subjects.isNotEmpty() }
        val groups = objects(m.optJSONArray("selection_groups")).map {
            SelectionGroup(
                it.optString("label").ifBlank { it.optString("name") }.trim(),
                listOf("slot_subject", "slot", "subject").firstNotNullOfOrNull { k -> it.optString(k).trim().takeIf { v -> v.isNotEmpty() } }.orEmpty(),
                strings(it.optJSONArray("subjects")),
            )
        }
        return ExamPolicy(
            examName = p.optString("current_exam_name").trim(),
            rules = rules,
            groups = groups,
            submitAfter = stringMap(m.optJSONObject("subject_submit_after")),
            questionCounts = intMap(m.optJSONObject("subject_question_counts")),
            descriptiveCounts = intMap(m.optJSONObject("subject_descriptive_question_counts")),
        )
    }

    /** POST /api/grades/admin/exam-policy body; every section is sent so removals stick. */
    fun toPayload(p: ExamPolicy): JSONObject = JSONObject()
        .put("current_exam_name", p.examName.trim())
        .put(
            "subject_mapping",
            JSONObject()
                .put("rules", JSONArray().apply {
                    p.rules.forEach { r ->
                        put(JSONObject().put("grade", r.grade).put("class_start", r.classStart).put("class_end", r.classEnd).put("subjects", JSONArray(r.subjects)))
                    }
                })
                .put("selection_groups", JSONArray().apply {
                    p.groups.forEach { g -> put(JSONObject().put("label", g.label).put("slot_subject", g.slot).put("subjects", JSONArray(g.subjects))) }
                })
                .put("subject_submit_after", JSONObject(p.submitAfter.filterValues { it.isNotBlank() }))
                .put("subject_question_counts", JSONObject(p.questionCounts.filterValues { it > 0 }))
                .put("subject_descriptive_question_counts", JSONObject(p.descriptiveCounts.filterValues { it > 0 })),
        )

    /** Every subject the policy mentions, in first-seen order (rules first). */
    fun subjects(p: ExamPolicy): List<String> = LinkedHashSet<String>().apply {
        p.rules.forEach { addAll(it.subjects) }
        p.groups.forEach { g -> if (g.slot.isNotBlank()) add(g.slot); addAll(g.subjects) }
        addAll(p.submitAfter.keys)
        addAll(p.questionCounts.keys)
        addAll(p.descriptiveCounts.keys)
    }.filter { it.isNotBlank() }

    /** "1학년 1~4반" / "전체 학년" … */
    fun describeTarget(r: SubjectRule): String {
        val grade = if (r.grade.isBlank()) "전체 학년" else "${r.grade}학년"
        val classes = when {
            r.classStart.isBlank() && r.classEnd.isBlank() -> "전체 반"
            r.classStart == r.classEnd || r.classEnd.isBlank() -> "${r.classStart}반"
            r.classStart.isBlank() -> "~${r.classEnd}반"
            else -> "${r.classStart}~${r.classEnd}반"
        }
        return "$grade $classes"
    }

    /** null when the rule is acceptable, else the reason. */
    fun validateRule(r: SubjectRule): String? {
        if (r.subjects.isEmpty()) return "과목을 하나 이상 입력해 주세요."
        val start = r.classStart.ifBlank { null }?.toIntOrNull()
        val end = r.classEnd.ifBlank { null }?.toIntOrNull()
        if (r.classStart.isNotBlank() && (start == null || start !in 1..30)) return "시작 반은 1~30 사이 숫자여야 합니다."
        if (r.classEnd.isNotBlank() && (end == null || end !in 1..30)) return "끝 반은 1~30 사이 숫자여야 합니다."
        if (start != null && end != null && start > end) return "시작 반이 끝 반보다 클 수 없습니다."
        return null
    }

    /**
     * Groups need two or more subjects and a subject may belong to only one group
     * (the server silently drops duplicates, which would lose the admin's edit).
     */
    fun validateGroup(groups: List<SelectionGroup>, index: Int?, g: SelectionGroup): String? {
        if (g.subjects.size < 2) return "선택과목 묶음에는 과목이 2개 이상 필요합니다."
        val others = groups.filterIndexed { i, _ -> i != index }.flatMap { it.subjects }.toSet()
        val dup = g.subjects.filter { it in others }
        if (dup.isNotEmpty()) return "다른 묶음에 이미 있는 과목입니다: ${dup.joinToString(", ")}"
        return null
    }

    fun validateCounts(questions: Int, descriptive: Int): String? =
        if (descriptive > questions) "서술형 문항 수는 전체 문항 수보다 많을 수 없습니다." else null

    /** Moves the rule at [from] to [to] (rules are matched top to bottom). */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices) return list
        val m = list.toMutableList()
        val item = m.removeAt(from)
        m.add(to.coerceIn(0, m.size), item)
        return m
    }

    /** Splits "국어, 수학 영어" style input into distinct subjects. */
    fun splitSubjects(text: String): List<String> = text.split(',', '\n', '/').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun objects(a: JSONArray?) = (0 until (a?.length() ?: 0)).mapNotNull { a!!.optJSONObject(it) }
    private fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.optString(it).trim() }.filter { it.isNotEmpty() && it != "null" }.distinct()
    private fun stringMap(o: JSONObject?) = o?.keys()?.asSequence()?.associate { it.trim() to o.optString(it).trim() }?.filter { it.key.isNotEmpty() && it.value.isNotEmpty() }.orEmpty()
    private fun intMap(o: JSONObject?) = o?.keys()?.asSequence()?.mapNotNull { k -> o.optString(k).toDoubleOrNull()?.toInt()?.let { k.trim() to it } }?.toMap().orEmpty()
}

/** 등급컷 수동 입력: 1·2학년 5등급, 3학년 9등급 (server _subject_grade_config). */
object ManualCutoffs {
    val LEVELS = listOf("1" to "1학년 (5등급)", "2" to "2학년 (5등급)", "3" to "3학년 (9등급)")

    fun gradeCount(level: String): Int = if (level == "3") 9 else 5

    /**
     * Rows for POST /admin/manual-grade-cutoffs. Blank = 산출불가 (null). Scores must be
     * 0~100 and must not rise as the grade number grows.
     */
    fun build(level: String, values: List<String>): Result<JSONArray> {
        val count = gradeCount(level)
        val scores = (0 until count).map { i -> values.getOrNull(i)?.trim().orEmpty() }
        var prev: Double? = null
        val rows = JSONArray()
        scores.forEachIndexed { i, raw ->
            val grade = i + 1
            val score = if (raw.isEmpty()) null else raw.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("${grade}등급 점수를 숫자로 입력해 주세요."))
            if (score != null) {
                if (score < 0 || score > 100) return Result.failure(IllegalArgumentException("${grade}등급 점수는 0~100 사이여야 합니다."))
                if (prev != null && score > prev!!) return Result.failure(IllegalArgumentException("${grade}등급 컷이 위 등급보다 높습니다."))
                prev = score
            }
            rows.put(JSONObject().put("grade", grade.toString()).put("grade_level", level).put("cutoff_score", score ?: JSONObject.NULL))
        }
        if ((0 until rows.length()).all { rows.getJSONObject(it).isNull("cutoff_score") }) {
            return Result.failure(IllegalArgumentException("적어도 한 등급의 컷 점수를 입력해 주세요."))
        }
        return Result.success(rows)
    }

    /** Existing values for [level] from manual or computed rows (grade → "92.5"). */
    fun prefill(rows: List<JSONObject>, level: String): List<String> {
        val count = gradeCount(level)
        val byGrade = rows.filter { it.optString("grade_level").let { l -> l.isBlank() || l == level } }
            .associate { it.optString("grade").toIntOrNull() to it }
        return (1..count).map { g ->
            val r = byGrade[g] ?: return@map ""
            if (r.isNull("cutoff_score")) "" else r.optDouble("cutoff_score").let { v -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString() }
        }
    }

    /** The school year the rows are for (first non-blank grade_level), default 1. */
    fun levelOf(rows: List<JSONObject>): String = rows.map { it.optString("grade_level") }.firstOrNull { it in setOf("1", "2", "3") } ?: "1"
}
