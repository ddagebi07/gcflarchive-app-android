package kr.co.gcflarchive.app.grade

import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.time.Instant
import java.util.Locale

/** 선택과목 묶음: one input row where the student picks which subject they took. */
data class SelectionGroup(val label: String, val slotSubject: String, val subjects: List<String>)

/** The admin-set exam policy (/api/grades/my-entry-policy, also echoed by my-profile). */
data class GradePolicy(
    val examName: String,
    val allowedSubjects: List<String>,
    val selectionGroups: List<SelectionGroup>,
    val submitAfter: Map<String, Instant>,
    val questionCounts: Map<String, Int>,
    val descriptiveCounts: Map<String, Int>,
    val statsSubjects: List<String>,
    /** Server clock minus device clock, so submit windows open on server time. */
    val clockOffsetMillis: Long,
)

data class CurrentEntry(val score: Double, val wrongQuestions: List<Int>)

data class MyScore(
    val subject: String,
    val examName: String,
    val score: String,
    val percentile: String,
    val grade: Int?,
    val timestamp: String,
)

data class GradeProfile(
    val policy: GradePolicy,
    val current: Map<String, CurrentEntry>,
    val submittedSubjects: List<String>,
    val scores: List<MyScore>,
    val allSubjects: List<String>,
)

data class CutoffRow(val grade: Int, val score: Double?)

data class CutoffResult(
    val visible: Boolean,
    val sampleCount: Int,
    val requiredSampleCount: Int,
    val averageScore: Double,
    val statsMode: String,
    val publicationTime: String,
    val rows: List<CutoffRow>,
)

data class WrongRate(val questionNo: Int, val percentage: Double, val wrong: Int, val responses: Int)

enum class WrongRateSort { QUESTION_ASC, QUESTION_DESC, RATE_DESC, RATE_ASC }

/** One row of 원점수 입력: a fixed subject, or a 선택과목 group with a subject picker. */
sealed interface InputRow {
    /** Subject whose submit window applies to this row. */
    val scheduleSubject: String

    data class Fixed(val subject: String) : InputRow {
        override val scheduleSubject: String get() = subject
    }

    data class Group(val group: SelectionGroup, override val scheduleSubject: String) : InputRow
}

data class OpenInfo(val open: Boolean, val availableAt: Instant?)

/** Rules from grade-calculator.html, kept free of Android types for unit tests. */
object GradeLogic {
    const val STATS_ALL = "all"
    const val STATS_VERIFIED = "verified_only"
    private val ko: Collator = Collator.getInstance(Locale.KOREAN)

    fun isTestUser(id: String?): Boolean = id?.toIntOrNull()?.let { it in 60000..99999 } == true

    fun normalizeGroups(raw: JSONArray?): List<SelectionGroup> {
        val seen = HashSet<String>()
        val out = mutableListOf<SelectionGroup>()
        for (i in 0 until (raw?.length() ?: 0)) {
            val g = raw!!.optJSONObject(i) ?: continue
            val subjects = strings(g.optJSONArray("subjects")).filter { it !in seen }
            if (subjects.size < 2) continue
            seen += subjects
            out += SelectionGroup(
                label = g.optString("label").ifBlank { g.optString("name") }.trim(),
                slotSubject = listOf("slot_subject", "slot", "subject").firstNotNullOfOrNull { k -> g.optString(k).trim().takeIf { it.isNotEmpty() } }.orEmpty(),
                subjects = subjects,
            )
        }
        return out
    }

    fun parsePolicy(json: JSONObject, deviceNow: Instant = Instant.now(), fallback: GradePolicy? = null): GradePolicy {
        val serverTime = parseInstant(json.optString("server_time"))
        return GradePolicy(
            examName = json.optString("current_exam_name").ifBlank { fallback?.examName.orEmpty() },
            allowedSubjects = json.optJSONArray("allowed_subjects")?.let(::strings) ?: fallback?.allowedSubjects.orEmpty(),
            selectionGroups = json.optJSONArray("selection_groups")?.let(::normalizeGroups) ?: fallback?.selectionGroups.orEmpty(),
            submitAfter = json.optJSONObject("subject_submit_after")?.let { o ->
                o.keys().asSequence().mapNotNull { k -> parseInstant(o.optString(k))?.let { k.trim() to it } }.toMap()
            } ?: fallback?.submitAfter.orEmpty(),
            questionCounts = json.optJSONObject("subject_question_counts")?.let(::intMap) ?: fallback?.questionCounts.orEmpty(),
            descriptiveCounts = json.optJSONObject("subject_descriptive_question_counts")?.let(::intMap) ?: fallback?.descriptiveCounts.orEmpty(),
            statsSubjects = json.optJSONArray("stats_subjects")?.let(::strings) ?: fallback?.statsSubjects.orEmpty(),
            clockOffsetMillis = serverTime?.let { it.toEpochMilli() - deviceNow.toEpochMilli() } ?: fallback?.clockOffsetMillis ?: 0,
        )
    }

    fun parseProfile(json: JSONObject, fallback: GradePolicy?, deviceNow: Instant = Instant.now()): GradeProfile {
        val policy = parsePolicy(json, deviceNow, fallback)
        val currentObj = json.optJSONObject("current_exam_scores")
        val current = currentObj?.keys()?.asSequence()?.mapNotNull { k ->
            val o = currentObj.optJSONObject(k) ?: return@mapNotNull null
            if (!o.has("score") || o.isNull("score") || o.optString("score").isBlank()) return@mapNotNull null
            k to CurrentEntry(o.optDouble("score"), ints(o.optJSONArray("wrong_questions")))
        }?.toMap().orEmpty()
        val scoresArr = json.optJSONArray("scores")
        val scores = (0 until (scoresArr?.length() ?: 0)).mapNotNull { scoresArr!!.optJSONObject(it) }.map { s ->
            MyScore(
                subject = s.optString("subject").ifBlank { "-" },
                examName = s.optString("exam_name").ifBlank { "-" },
                score = display(s, "score"),
                percentile = display(s, "percentile"),
                grade = if (s.has("grade") && !s.isNull("grade")) s.optInt("grade").takeIf { it > 0 } else null,
                timestamp = s.optString("timestamp"),
            )
        }.sortedByDescending { it.timestamp }
        val submitted = json.optJSONArray("submitted_subjects")?.let(::strings)
            ?: scores.map { it.subject }.filter { it != "-" }.distinct()
        val all = (policy.allowedSubjects + policy.selectionGroups.flatMap { it.subjects } + scores.map { it.subject }.filter { it != "-" })
            .toSortedSet(ko).toList()
        return GradeProfile(policy, current, submitted, scores, all)
    }

    fun openInfo(subject: String, policy: GradePolicy, deviceNow: Instant = Instant.now()): OpenInfo {
        if (subject.isBlank()) return OpenInfo(false, null)
        val at = policy.submitAfter[subject.trim()] ?: return OpenInfo(true, null)
        return OpenInfo(deviceNow.toEpochMilli() + policy.clockOffsetMillis >= at.toEpochMilli(), at)
    }

    /** The subject whose time gates a 선택과목 row (slot if it has a time, else its first subject). */
    fun groupScheduleSubject(group: SelectionGroup, policy: GradePolicy): String {
        val first = group.subjects.firstOrNull().orEmpty()
        if (group.slotSubject == "선택과목") return first
        return if (group.slotSubject.isNotBlank() && policy.submitAfter.containsKey(group.slotSubject)) group.slotSubject else first
    }

    private fun scheduleMap(policy: GradePolicy): Map<String, String> {
        val map = HashMap<String, String>()
        for (g in policy.selectionGroups) {
            val s = groupScheduleSubject(g, policy)
            if (s.isBlank()) continue
            if (g.slotSubject.isNotBlank()) map[g.slotSubject] = s
            g.subjects.forEach { map[it] = s }
        }
        return map
    }

    /** Subjects with an opening time first (earliest first), then the rest in Korean order. */
    fun orderBySubmitOpen(subjects: List<String>, policy: GradePolicy): List<String> {
        val sched = scheduleMap(policy)
        return subjects.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sortedWith { l, r ->
            val ls = sched[l] ?: l
            val rs = sched[r] ?: r
            val la = policy.submitAfter[ls]
            val ra = policy.submitAfter[rs]
            when {
                la != null && ra != null && la != ra -> la.compareTo(ra)
                la != null && ra == null -> -1
                la == null && ra != null -> 1
                else -> ko.compare(ls, rs).takeIf { it != 0 } ?: ko.compare(l, r)
            }
        }
    }

    fun inputRows(policy: GradePolicy): List<InputRow> {
        val selection = policy.selectionGroups.flatMap { listOf(it.slotSubject) + it.subjects }.filter { it.isNotBlank() }.toSet()
        val fixed = orderBySubmitOpen(policy.allowedSubjects.filter { it !in selection }, policy).map { InputRow.Fixed(it) }
        val groups = policy.selectionGroups.map { InputRow.Group(it, groupScheduleSubject(it, policy)) }
            .sortedWith { a, b ->
                val aa = policy.submitAfter[a.scheduleSubject]
                val ba = policy.submitAfter[b.scheduleSubject]
                when {
                    a.scheduleSubject.isBlank() && b.scheduleSubject.isBlank() -> 0
                    a.scheduleSubject.isBlank() -> 1
                    b.scheduleSubject.isBlank() -> -1
                    aa != null && ba != null && aa != ba -> aa.compareTo(ba)
                    aa != null && ba == null -> -1
                    aa == null && ba != null -> 1
                    else -> ko.compare(a.scheduleSubject, b.scheduleSubject)
                }
            }
        return fixed + groups
    }

    fun questionCount(subject: String, policy: GradePolicy): Int = policy.questionCounts[subject]?.coerceAtLeast(0) ?: 0

    /** "12" for 객관식, "서술형2" for the last descriptive questions. */
    fun questionLabel(subject: String, n: Int, policy: GradePolicy): String {
        val total = questionCount(subject, policy)
        val desc = minOf(total, policy.descriptiveCounts[subject]?.coerceAtLeast(0) ?: 0)
        return if (desc <= 0 || n <= total - desc) n.toString() else "서술형${n - (total - desc)}"
    }

    /** Parses 0~100; failures carry the web page's message. */
    fun parseScore(raw: String): Result<Double> {
        val v = raw.trim().toDoubleOrNull() ?: return Result.failure(IllegalArgumentException("점수를 입력하세요."))
        if (v < 0 || v > 100) return Result.failure(IllegalArgumentException("점수는 0~100 사이여야 합니다."))
        return Result.success(v)
    }

    fun formatScore(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun parseCutoff(json: JSONObject): CutoffResult {
        val arr = json.optJSONArray("grade_cutoffs")
        val rows = (0 until (arr?.length() ?: 0)).mapNotNull { arr!!.optJSONObject(it) }.map { r ->
            CutoffRow(r.optInt("grade"), if (r.has("cutoff_score") && !r.isNull("cutoff_score")) r.optDouble("cutoff_score") else null)
        }
        return CutoffResult(
            visible = json.optBoolean("visible"),
            sampleCount = json.optInt("sample_count"),
            requiredSampleCount = json.optInt("required_sample_count"),
            averageScore = json.optDouble("average_score", 0.0).let { if (it.isNaN()) 0.0 else it },
            statsMode = json.optString("stats_mode").ifBlank { STATS_ALL },
            publicationTime = json.optString("publication_time"),
            rows = rows,
        )
    }

    /** Best grade whose cutoff [myScore] reaches (1등급 is best). */
    fun myGrade(rows: List<CutoffRow>, myScore: Double?): Int? {
        myScore ?: return null
        return rows.filter { it.score != null && myScore >= it.score }.minOfOrNull { it.grade }
    }

    fun parseWrongRates(json: JSONObject): List<WrongRate> {
        val arr = json.optJSONArray("questions")
        return (0 until (arr?.length() ?: 0)).mapNotNull { arr!!.optJSONObject(it) }.map {
            WrongRate(it.optInt("question_no"), it.optDouble("wrong_percentage", 0.0), it.optInt("wrong_count"), it.optInt("response_count"))
        }
    }

    fun sortWrongRates(rows: List<WrongRate>, sort: WrongRateSort): List<WrongRate> = when (sort) {
        WrongRateSort.QUESTION_ASC -> rows.sortedBy { it.questionNo }
        WrongRateSort.QUESTION_DESC -> rows.sortedByDescending { it.questionNo }
        WrongRateSort.RATE_DESC -> rows.sortedWith(compareByDescending<WrongRate> { it.percentage }.thenBy { it.questionNo })
        WrongRateSort.RATE_ASC -> rows.sortedWith(compareBy<WrongRate> { it.percentage }.thenBy { it.questionNo })
    }

    /** Subjects the cutoff / 오답률 pickers offer, by user kind (grade-calculator.html). */
    fun statsSubjects(profile: GradeProfile, isTestUser: Boolean, isAdmin: Boolean): List<String> = when {
        isTestUser -> orderBySubmitOpen(profile.allSubjects.ifEmpty { listOf("국어", "수학", "영어", "과학", "사회", "기타") }, profile.policy)
        isAdmin -> orderBySubmitOpen(profile.policy.statsSubjects, profile.policy)
        else -> orderBySubmitOpen(profile.submittedSubjects, profile.policy)
    }

    fun parseInstant(s: String?): Instant? {
        val t = s?.trim().orEmpty()
        if (t.isEmpty()) return null
        return runCatching { Instant.parse(t) }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(t).toInstant() }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(t).toInstant(java.time.ZoneOffset.UTC) }.getOrNull()
    }

    private fun strings(arr: JSONArray?): List<String> =
        (0 until (arr?.length() ?: 0)).map { arr!!.optString(it).trim() }.filter { it.isNotEmpty() }

    private fun ints(arr: JSONArray?): List<Int> =
        (0 until (arr?.length() ?: 0)).map { arr!!.optInt(it) }.filter { it > 0 }.distinct().sorted()

    private fun intMap(o: JSONObject): Map<String, Int> =
        o.keys().asSequence().mapNotNull { k -> o.optDouble(k).takeIf { !it.isNaN() }?.let { k.trim() to it.toInt() } }.toMap()

    private fun display(o: JSONObject, key: String): String =
        if (!o.has(key) || o.isNull(key)) "-" else o.opt(key).toString().let { v -> v.toDoubleOrNull()?.let(::formatScore) ?: v }
}
