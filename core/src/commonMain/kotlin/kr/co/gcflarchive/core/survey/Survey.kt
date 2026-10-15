package kr.co.gcflarchive.core.survey

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kr.co.gcflarchive.core.net.JObject
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.objects
import kr.co.gcflarchive.core.net.parseObject
import kr.co.gcflarchive.core.net.strings

/** 설문조사 (routes/surveys.py, services/survey_logic.py). */
enum class QuestionType(val apiValue: String, val label: String) {
    SHORT("short", "단답형"),
    PARAGRAPH("paragraph", "장문형"),
    CHOICE("choice", "객관식 질문"),
    CHECKBOX("checkbox", "체크박스"),
    DROPDOWN("dropdown", "드롭다운"),
    SCALE("scale", "선형 배율"),
    DATE("date", "날짜"),
    TIME("time", "시간"),
    SECTION("section", "구역");

    val hasOptions get() = this == CHOICE || this == CHECKBOX || this == DROPDOWN

    companion object {
        fun of(value: String) = entries.firstOrNull { it.apiValue == value } ?: SHORT
    }
}

data class SurveyQuestion(
    val id: String,
    val type: QuestionType,
    val title: String,
    val description: String = "",
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    /** 기타 (직접 입력) for 객관식·체크박스. */
    val other: Boolean = false,
    val min: Int = 1,
    val max: Int = 5,
    val minLabel: String = "",
    val maxLabel: String = "",
)

/** A survey in a list. [responseCount] / [audienceLabel] only for the people who manage it. */
data class SurveySummary(
    val id: Int,
    val title: String,
    val description: String,
    val ownerName: String,
    val status: String,
    val statusLabel: String,
    val closesAt: String,
    val questionCount: Int,
    val responded: Boolean,
    val canManage: Boolean,
    val collectIdentity: Boolean,
    val responseCount: Int?,
    val audienceLabel: String,
)

data class SurveyList(
    val loggedIn: Boolean,
    val canCreate: Boolean,
    val surveys: List<SurveySummary>,
    val mine: List<SurveySummary>,
    /** '응답할 수 있는 사람' choices for the builder (only when [canCreate]). */
    val scopes: List<ScopeOption> = emptyList(),
)

/** An answer: text (short/paragraph/choice/dropdown/date/time), several picks (checkbox) or a number (scale). */
sealed interface Answer {
    data class Text(val value: String) : Answer
    data class Picks(val values: List<String>) : Answer
    data class Number(val value: Int) : Answer

    fun isBlank(): Boolean = when (this) {
        is Text -> value.isBlank()
        is Picks -> values.isEmpty()
        is Number -> false
    }
}

data class SurveyForm(
    val summary: SurveySummary,
    val description: String,
    val questions: List<SurveyQuestion>,
    val accepting: Boolean,
    val allowEdit: Boolean,
    val showSummary: Boolean,
    val myAnswers: Map<String, Answer>?,
    val canRespond: Boolean,
    val canSeeSummary: Boolean,
    /** For the owner's 수정 화면. */
    val audienceScope: List<String>,
    val rawStatus: String,
)

data class OptionCount(val text: String, val count: Int, val other: Boolean)

data class QuestionSummary(
    val id: String,
    val type: QuestionType,
    val title: String,
    val answered: Int,
    val options: List<OptionCount>,
    val otherTexts: List<String>,
    val scale: List<Pair<Int, Int>>,
    val average: Double?,
    val minLabel: String,
    val maxLabel: String,
    val texts: List<String>,
)

data class SurveyResponseRow(val number: Int, val respondent: String, val submittedAt: String, val answers: Map<String, String>)

data class SurveyResults(
    val title: String,
    val responseCount: Int,
    val summary: List<QuestionSummary>,
    /** Owner only: column titles and every response. */
    val questions: List<Pair<String, String>>,
    val collectIdentity: Boolean,
    val responses: List<SurveyResponseRow>,
)

/** A choice for 대상 / 만들 수 있는 사람 (community scopes and roles). */
data class ScopeOption(val value: String, val label: String)

data class SurveySettings(val creatorScope: List<String>, val creatorLabel: String, val scopes: List<ScopeOption>)

/** What the 설문 만들기 screen sends. */
data class SurveyDraft(
    val title: String,
    val description: String,
    val audienceScope: List<String>,
    val status: String,
    val closesAt: String,
    val collectIdentity: Boolean,
    val allowEdit: Boolean,
    val showSummary: Boolean,
    val questions: List<SurveyQuestion>,
)

object SurveyLogic {
    private fun summary(j: JObject) = SurveySummary(
        id = j.optInt("id"),
        title = j.optString("title"),
        description = j.optString("description"),
        ownerName = j.optString("ownerName"),
        status = j.optString("status"),
        statusLabel = j.optString("statusLabel"),
        closesAt = j.optString("closesAt"),
        questionCount = j.optInt("questionCount"),
        responded = j.optBoolean("responded"),
        canManage = j.optBoolean("canManage"),
        collectIdentity = j.optBoolean("collectIdentity"),
        responseCount = if (j.has("responseCount")) j.optInt("responseCount") else null,
        audienceLabel = j.optString("audienceLabel"),
    )

    fun parseList(body: String): SurveyList = parseObject(body).let { j ->
        SurveyList(
            j.optBoolean("loggedIn"), j.optBoolean("canCreate"),
            j.optJSONArray("surveys").objects().map(::summary).filter { it.id > 0 },
            j.optJSONArray("mine").objects().map(::summary).filter { it.id > 0 },
            j.optJSONArray("scopes").objects().map { ScopeOption(it.optString("value"), it.optString("label")) },
        )
    }

    fun question(j: JObject) = SurveyQuestion(
        id = j.optString("id"),
        type = QuestionType.of(j.optString("type")),
        title = j.optString("title"),
        description = j.optString("description"),
        required = j.optBoolean("required"),
        options = j.optJSONArray("options").strings(),
        other = j.optBoolean("other"),
        min = if (j.has("min")) j.optInt("min") else 1,
        max = if (j.has("max")) j.optInt("max") else 5,
        minLabel = j.optString("minLabel"),
        maxLabel = j.optString("maxLabel"),
    )

    private fun answer(type: QuestionType, value: JsonElement?): Answer? = when {
        value == null -> null
        value is JsonArray -> Answer.Picks(value.mapNotNull { (it as? JsonPrimitive)?.content })
        type == QuestionType.SCALE -> (value as? JsonPrimitive)?.content?.toIntOrNull()?.let { Answer.Number(it) }
        value is JsonPrimitive -> Answer.Text(value.content)
        else -> null
    }

    fun parseForm(body: String): SurveyForm = parseObject(body).let { j ->
        val questions = j.optJSONArray("questions").objects().map(::question)
        val mine = j.optJSONObject("myAnswers")?.raw?.let { raw ->
            questions.mapNotNull { q -> answer(q.type, raw[q.id])?.let { q.id to it } }.toMap()
        }
        SurveyForm(
            summary = summary(j),
            description = j.optString("description"),
            questions = questions,
            accepting = j.optBoolean("accepting"),
            allowEdit = j.optBoolean("allowEdit"),
            showSummary = j.optBoolean("showSummary"),
            myAnswers = mine,
            canRespond = j.optBoolean("canRespond"),
            canSeeSummary = j.optBoolean("canSeeSummary"),
            audienceScope = j.optJSONArray("audienceScope").strings(),
            rawStatus = j.optString("rawStatus").ifBlank { j.optString("status") },
        )
    }

    fun parseResults(body: String): SurveyResults = parseObject(body).let { j ->
        SurveyResults(
            title = j.optString("title"),
            responseCount = j.optInt("responseCount"),
            summary = j.optJSONArray("summary").objects().map { s ->
                QuestionSummary(
                    id = s.optString("id"),
                    type = QuestionType.of(s.optString("type")),
                    title = s.optString("title"),
                    answered = s.optInt("answered"),
                    options = s.optJSONArray("options").objects().map { OptionCount(it.optString("text"), it.optInt("count"), it.optBoolean("other")) },
                    otherTexts = s.optJSONArray("otherTexts").strings(),
                    scale = s.optJSONArray("scale").objects().map { it.optInt("value") to it.optInt("count") },
                    average = if (s.isNull("average")) null else s.optString("average").toDoubleOrNull(),
                    minLabel = s.optString("minLabel"),
                    maxLabel = s.optString("maxLabel"),
                    texts = s.optJSONArray("texts").strings(),
                )
            },
            questions = j.optJSONArray("questions").objects().map { it.optString("id") to it.optString("title") },
            collectIdentity = j.optBoolean("collectIdentity"),
            responses = j.optJSONArray("responses").objects().map { r ->
                val raw = r.optJSONObject("answers")?.raw.orEmpty()
                SurveyResponseRow(
                    number = r.optInt("number"),
                    respondent = r.optString("respondent"),
                    submittedAt = r.optString("submittedAt"),
                    answers = raw.mapValues { (_, v) ->
                        if (v is JsonArray) v.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(", ") else (v as? JsonPrimitive)?.content.orEmpty()
                    },
                )
            },
        )
    }

    fun parseSettings(body: String): SurveySettings = parseObject(body).let { j ->
        SurveySettings(
            creatorScope = j.optJSONArray("creatorScope").strings(),
            creatorLabel = j.optString("creatorLabel"),
            scopes = j.optJSONArray("scopes").objects().map { ScopeOption(it.optString("value"), it.optString("label")) },
        )
    }

    /** Same checks as the server, so the form can point at the question before sending. Returns (question id, message). */
    fun firstProblem(questions: List<SurveyQuestion>, answers: Map<String, Answer>): Pair<String, String>? {
        questions.forEachIndexed { n, q ->
            if (q.type == QuestionType.SECTION) return@forEachIndexed
            val a = answers[q.id]
            val label = q.title.ifBlank { "${n + 1}번 문항" }
            if (a == null || a.isBlank()) {
                if (q.required) return q.id to "'$label'에 답해주세요."
                return@forEachIndexed
            }
            when (q.type) {
                QuestionType.SHORT -> if ((a as? Answer.Text)?.value.orEmpty().length > 500) return q.id to "'$label'은(는) 500자까지 쓸 수 있어요."
                QuestionType.PARAGRAPH -> if ((a as? Answer.Text)?.value.orEmpty().length > 5000) return q.id to "'$label'은(는) 5,000자까지 쓸 수 있어요."
                QuestionType.DATE -> if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches((a as? Answer.Text)?.value.orEmpty())) return q.id to "'$label'의 날짜를 골라주세요."
                QuestionType.TIME -> if (!Regex("([01]\\d|2[0-3]):[0-5]\\d").matches((a as? Answer.Text)?.value.orEmpty())) return q.id to "'$label'의 시간을 골라주세요."
                else -> Unit
            }
        }
        return null
    }

    /** Builder checks before saving (the server checks again). */
    fun draftProblem(d: SurveyDraft): String? = when {
        d.title.isBlank() -> "설문 제목을 입력해주세요."
        d.title.length > 200 -> "설문 제목은 200자까지 쓸 수 있어요."
        d.audienceScope.isEmpty() -> "응답할 수 있는 사람을 하나 이상 골라주세요."
        d.questions.none { it.type != QuestionType.SECTION } -> "답할 수 있는 문항을 하나 이상 만들어주세요."
        else -> d.questions.withIndex().firstNotNullOfOrNull { (i, q) ->
            when {
                q.type != QuestionType.SECTION && q.title.isBlank() -> "${i + 1}번 문항의 질문을 입력해주세요."
                q.type.hasOptions && q.options.none { it.isNotBlank() } -> "${i + 1}번 문항에 선택지를 하나 이상 넣어주세요."
                else -> null
            }
        }
    }

    fun answersJson(answers: Map<String, Answer>): JsonObject = JsonObject(answers.mapValues { (_, a) ->
        when (a) {
            is Answer.Text -> JsonPrimitive(a.value.trim())
            is Answer.Picks -> JsonArray(a.values.map { JsonPrimitive(it) })
            is Answer.Number -> JsonPrimitive(a.value)
        }
    })

    fun draftJson(d: SurveyDraft): JsonObject = buildJsonObject {
        put("title", d.title.trim())
        put("description", d.description.trim())
        put("audienceScope", JsonArray(d.audienceScope.map { JsonPrimitive(it) }))
        put("status", d.status)
        put("closesAt", d.closesAt)
        put("collectIdentity", d.collectIdentity)
        put("allowEdit", d.allowEdit)
        put("showSummary", d.showSummary)
        put("questions", JsonArray(d.questions.map { q ->
            buildJsonObject {
                put("id", q.id)
                put("type", q.type.apiValue)
                put("title", q.title.trim())
                put("description", q.description.trim())
                put("required", q.required)
                if (q.type.hasOptions) {
                    put("options", JsonArray(q.options.map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) }))
                    put("other", q.other)
                }
                if (q.type == QuestionType.SCALE) {
                    put("min", q.min); put("max", q.max); put("minLabel", q.minLabel); put("maxLabel", q.maxLabel)
                }
            }
        }))
    }
}

class SurveyException(val code: Int, message: String, val loginRequired: Boolean = false) : Exception(message)

class SurveyRepository(private val site: SiteClient) {
    private fun ok(res: SiteClient.Response): String {
        if (!res.isSuccessful) {
            val j = parseObject(res.body)
            val message = j.optString("error").ifBlank { if (res.code == 401) "로그인하면 설문에 참여할 수 있어요." else "인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요." }
            throw SurveyException(res.code, message, loginRequired = res.code == 401 || j.optBoolean("loginRequired"))
        }
        return res.body
    }

    suspend fun list(): SurveyList = SurveyLogic.parseList(ok(site.exchange("/api/surveys")))

    suspend fun form(id: Int): SurveyForm = SurveyLogic.parseForm(ok(site.exchange("/api/surveys/$id")))

    suspend fun create(draft: SurveyDraft): Int =
        parseObject(ok(site.exchange("/api/surveys", "POST", SurveyLogic.draftJson(draft)))).optJSONObject("survey")?.optInt("id") ?: 0

    suspend fun update(id: Int, draft: SurveyDraft) {
        ok(site.exchange("/api/surveys/$id", "PUT", SurveyLogic.draftJson(draft)))
    }

    suspend fun setStatus(id: Int, status: String) {
        ok(site.exchange("/api/surveys/$id", "PUT", buildJsonObject { put("status", status) }))
    }

    suspend fun delete(id: Int) {
        ok(site.exchange("/api/surveys/$id", "DELETE"))
    }

    /** Returns true when an earlier response was replaced (응답 수정). */
    suspend fun submit(id: Int, answers: Map<String, Answer>): Boolean {
        val body = buildJsonObject { put("answers", SurveyLogic.answersJson(answers)) }
        return parseObject(ok(site.exchange("/api/surveys/$id/responses", "POST", body))).optBoolean("updated")
    }

    suspend fun results(id: Int): SurveyResults = SurveyLogic.parseResults(ok(site.exchange("/api/surveys/$id/results")))

    suspend fun csv(id: Int): ByteArray = site.bytes("/api/surveys/$id/results.csv")

    suspend fun settings(): SurveySettings = SurveyLogic.parseSettings(ok(site.exchange("/api/surveys/admin/settings")))
}
