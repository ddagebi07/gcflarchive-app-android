package kr.co.gcflarchive.app.survey

import kr.co.gcflarchive.app.data.SiteApi
import org.json.JSONArray
import org.json.JSONObject

/** 설문조사 (routes/surveys.py). Same shapes as the shared core's survey models. */
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
    val other: Boolean = false,
    val min: Int = 1,
    val max: Int = 5,
    val minLabel: String = "",
    val maxLabel: String = "",
)

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
)

data class ScopeOption(val value: String, val label: String)

data class SurveyList(
    val loggedIn: Boolean,
    val canCreate: Boolean,
    val surveys: List<SurveySummary>,
    val mine: List<SurveySummary>,
    val scopes: List<ScopeOption>,
)

/** Text (short/paragraph/choice/dropdown/date/time), several picks (checkbox) or a number (scale). */
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
    val audienceScope: List<String>,
    val rawStatus: String,
)

data class OptionCount(val text: String, val count: Int)

data class QuestionSummary(
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

data class SurveyResponseRow(val respondent: String, val submittedAt: String, val answers: Map<String, String>)

data class SurveyResults(
    val title: String,
    val responseCount: Int,
    val summary: List<QuestionSummary>,
    val questions: List<Pair<String, String>>,
    val collectIdentity: Boolean,
    val responses: List<SurveyResponseRow>,
)

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

class SurveyException(val code: Int, message: String, val loginRequired: Boolean = false) : Exception(message)

object SurveyLogic {
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it).trim() }.filter { it.isNotEmpty() && it != "null" }

    private fun summary(j: JSONObject) = SurveySummary(
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
    )

    private fun scopes(arr: JSONArray?) = arr.objects().map { ScopeOption(it.optString("value"), it.optString("label")) }

    fun parseList(body: String): SurveyList = JSONObject(body).let { j ->
        SurveyList(
            j.optBoolean("loggedIn"), j.optBoolean("canCreate"),
            j.optJSONArray("surveys").objects().map(::summary).filter { it.id > 0 },
            j.optJSONArray("mine").objects().map(::summary).filter { it.id > 0 },
            scopes(j.optJSONArray("scopes")),
        )
    }

    fun question(j: JSONObject) = SurveyQuestion(
        id = j.optString("id"),
        type = QuestionType.of(j.optString("type")),
        title = j.optString("title"),
        description = j.optString("description"),
        required = j.optBoolean("required"),
        options = j.optJSONArray("options").strings(),
        other = j.optBoolean("other"),
        min = j.optInt("min", 1),
        max = j.optInt("max", 5),
        minLabel = j.optString("minLabel"),
        maxLabel = j.optString("maxLabel"),
    )

    private fun answer(type: QuestionType, value: Any?): Answer? = when {
        value == null || value == JSONObject.NULL -> null
        value is JSONArray -> Answer.Picks(value.strings())
        type == QuestionType.SCALE -> value.toString().toIntOrNull()?.let { Answer.Number(it) }
        else -> Answer.Text(value.toString())
    }

    fun parseForm(body: String): SurveyForm = JSONObject(body).let { j ->
        val questions = j.optJSONArray("questions").objects().map(::question)
        val mine = j.optJSONObject("myAnswers")?.let { raw ->
            questions.mapNotNull { q -> answer(q.type, raw.opt(q.id))?.let { q.id to it } }.toMap()
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

    fun parseResults(body: String): SurveyResults = JSONObject(body).let { j ->
        SurveyResults(
            title = j.optString("title"),
            responseCount = j.optInt("responseCount"),
            summary = j.optJSONArray("summary").objects().map { s ->
                QuestionSummary(
                    title = s.optString("title"),
                    answered = s.optInt("answered"),
                    options = s.optJSONArray("options").objects().map { OptionCount(it.optString("text"), it.optInt("count")) },
                    otherTexts = s.optJSONArray("otherTexts").strings(),
                    scale = s.optJSONArray("scale").objects().map { it.optInt("value") to it.optInt("count") },
                    average = if (s.isNull("average") || !s.has("average")) null else s.optDouble("average"),
                    minLabel = s.optString("minLabel"),
                    maxLabel = s.optString("maxLabel"),
                    texts = s.optJSONArray("texts").strings(),
                )
            },
            questions = j.optJSONArray("questions").objects().map { it.optString("id") to it.optString("title") },
            collectIdentity = j.optBoolean("collectIdentity"),
            responses = j.optJSONArray("responses").objects().map { r ->
                val raw = r.optJSONObject("answers") ?: JSONObject()
                SurveyResponseRow(
                    respondent = r.optString("respondent"),
                    submittedAt = r.optString("submittedAt"),
                    answers = raw.keys().asSequence().associateWith { k ->
                        when (val v = raw.opt(k)) {
                            is JSONArray -> v.strings().joinToString(", ")
                            null, JSONObject.NULL -> ""
                            else -> v.toString()
                        }
                    },
                )
            },
        )
    }

    /** Same checks as the server; returns (question id, message) for the first problem. */
    fun firstProblem(questions: List<SurveyQuestion>, answers: Map<String, Answer>): Pair<String, String>? {
        questions.forEachIndexed { n, q ->
            if (q.type == QuestionType.SECTION) return@forEachIndexed
            val a = answers[q.id]
            val label = q.title.ifBlank { "${n + 1}번 문항" }
            if (a == null || a.isBlank()) {
                if (q.required) return q.id to "'$label'에 답해주세요."
                return@forEachIndexed
            }
            val text = (a as? Answer.Text)?.value.orEmpty()
            when (q.type) {
                QuestionType.SHORT -> if (text.length > 500) return q.id to "'$label'은(는) 500자까지 쓸 수 있어요."
                QuestionType.PARAGRAPH -> if (text.length > 5000) return q.id to "'$label'은(는) 5,000자까지 쓸 수 있어요."
                QuestionType.DATE -> if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(text)) return q.id to "'$label'의 날짜를 골라주세요."
                QuestionType.TIME -> if (!Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(text)) return q.id to "'$label'의 시간을 골라주세요."
                else -> Unit
            }
        }
        return null
    }

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

    fun answersJson(answers: Map<String, Answer>): JSONObject = JSONObject().apply {
        answers.forEach { (k, a) ->
            when (a) {
                is Answer.Text -> put(k, a.value.trim())
                is Answer.Picks -> put(k, JSONArray(a.values))
                is Answer.Number -> put(k, a.value)
            }
        }
    }

    fun draftJson(d: SurveyDraft): JSONObject = JSONObject()
        .put("title", d.title.trim())
        .put("description", d.description.trim())
        .put("audienceScope", JSONArray(d.audienceScope))
        .put("status", d.status)
        .put("closesAt", d.closesAt)
        .put("collectIdentity", d.collectIdentity)
        .put("allowEdit", d.allowEdit)
        .put("showSummary", d.showSummary)
        .put("questions", JSONArray(d.questions.map { q ->
            JSONObject()
                .put("id", q.id)
                .put("type", q.type.apiValue)
                .put("title", q.title.trim())
                .put("description", q.description.trim())
                .put("required", q.required)
                .apply {
                    if (q.type.hasOptions) {
                        put("options", JSONArray(q.options.map { it.trim() }.filter { it.isNotEmpty() }))
                        put("other", q.other)
                    }
                    if (q.type == QuestionType.SCALE) {
                        put("min", q.min); put("max", q.max); put("minLabel", q.minLabel); put("maxLabel", q.maxLabel)
                    }
                }
        }))
}

/** /api/surveys/… with the site session cookie. */
object SurveyRepository {
    private fun ok(res: SiteApi.Response): String {
        if (!res.isSuccessful) {
            val j = res.json
            throw SurveyException(
                res.code,
                res.error ?: if (res.code == 401) "로그인하면 설문에 참여할 수 있어요." else "인터넷 연결이 불안정해요. 잠시 후 다시 시도해주세요.",
                loginRequired = res.code == 401 || j.optBoolean("loginRequired"),
            )
        }
        return res.body
    }

    suspend fun list(): SurveyList = SurveyLogic.parseList(ok(SiteApi.exchange("/api/surveys")))

    suspend fun form(id: Int): SurveyForm = SurveyLogic.parseForm(ok(SiteApi.exchange("/api/surveys/$id")))

    suspend fun create(draft: SurveyDraft): Int =
        JSONObject(ok(SiteApi.exchange("/api/surveys", "POST", SurveyLogic.draftJson(draft)))).optJSONObject("survey")?.optInt("id") ?: 0

    suspend fun update(id: Int, draft: SurveyDraft) {
        ok(SiteApi.exchange("/api/surveys/$id", "PUT", SurveyLogic.draftJson(draft)))
    }

    suspend fun setStatus(id: Int, status: String) {
        ok(SiteApi.exchange("/api/surveys/$id", "PUT", JSONObject().put("status", status)))
    }

    suspend fun delete(id: Int) {
        ok(SiteApi.exchange("/api/surveys/$id", "DELETE"))
    }

    /** True when an earlier response was replaced (응답 수정). */
    suspend fun submit(id: Int, answers: Map<String, Answer>): Boolean {
        val body = JSONObject().put("answers", SurveyLogic.answersJson(answers))
        return JSONObject(ok(SiteApi.exchange("/api/surveys/$id/responses", "POST", body))).optBoolean("updated")
    }

    suspend fun results(id: Int): SurveyResults = SurveyLogic.parseResults(ok(SiteApi.exchange("/api/surveys/$id/results")))

    suspend fun csv(id: Int): ByteArray = SiteApi.bytes("/api/surveys/$id/results.csv")
}
