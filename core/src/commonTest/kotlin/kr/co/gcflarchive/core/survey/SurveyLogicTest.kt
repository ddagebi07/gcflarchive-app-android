package kr.co.gcflarchive.core.survey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurveyLogicTest {
    private val formJson = """{"id":3,"title":"축제 설문","status":"open","statusLabel":"응답 받는 중","canManage":false,"responded":true,
        "questions":[{"id":"s","type":"section","title":"기본"},
                     {"id":"grade","type":"choice","title":"학년","options":["1학년","2학년"],"required":true},
                     {"id":"clubs","type":"checkbox","title":"동아리","options":["밴드"],"other":true},
                     {"id":"score","type":"scale","title":"만족도","min":1,"max":5,"minLabel":"별로"},
                     {"id":"day","type":"date","title":"날짜"}],
        "myAnswers":{"grade":"2학년","clubs":["밴드","로봇"],"score":4},"canRespond":true,"accepting":true}"""

    @Test
    fun parsesFormAndMyAnswers() {
        val f = SurveyLogic.parseForm(formJson)
        assertEquals(5, f.questions.size)
        assertEquals(QuestionType.SCALE, f.questions[3].type)
        assertEquals("별로", f.questions[3].minLabel)
        assertEquals(Answer.Text("2학년"), f.myAnswers!!["grade"])
        assertEquals(Answer.Picks(listOf("밴드", "로봇")), f.myAnswers!!["clubs"])
        assertEquals(Answer.Number(4), f.myAnswers!!["score"])
        assertNull(f.summary.responseCount)
    }

    @Test
    fun checksAnswersLikeTheServer() {
        val qs = SurveyLogic.parseForm(formJson).questions
        assertEquals("grade", SurveyLogic.firstProblem(qs, emptyMap())?.first)
        assertNull(SurveyLogic.firstProblem(qs, mapOf("grade" to Answer.Text("1학년"))))
        assertEquals("day", SurveyLogic.firstProblem(qs, mapOf("grade" to Answer.Text("1학년"), "day" to Answer.Text("20일")))?.first)
    }

    @Test
    fun parsesResults() {
        val r = SurveyLogic.parseResults("""{"title":"t","responseCount":2,"summary":[
            {"id":"grade","type":"choice","title":"학년","answered":2,"options":[{"text":"1학년","count":2},{"text":"기타","count":1,"other":true}],"otherTexts":["교사"]},
            {"id":"score","type":"scale","title":"만족도","answered":2,"scale":[{"value":1,"count":0},{"value":2,"count":2}],"average":2.0},
            {"id":"memo","type":"paragraph","title":"말","answered":1,"texts":["좋아요"]}],
            "questions":[{"id":"grade","title":"학년"}],"collectIdentity":true,
            "responses":[{"number":1,"respondent":"20102","submittedAt":"x","answers":{"grade":"1학년","clubs":["a","b"],"score":2}}]}""")
        assertEquals(2, r.summary[0].options[0].count)
        assertTrue(r.summary[0].options[1].other)
        assertEquals(listOf(1 to 0, 2 to 2), r.summary[1].scale)
        assertEquals(2.0, r.summary[1].average)
        assertEquals("a, b", r.responses[0].answers["clubs"])
        assertEquals("2", r.responses[0].answers["score"])
    }

    @Test
    fun draftChecksAndJson() {
        val q = SurveyQuestion("q1", QuestionType.CHOICE, "학년", options = listOf("1학년", " "))
        val d = SurveyDraft("설문", "", listOf("login"), "open", "", false, false, false, listOf(q))
        assertNull(SurveyLogic.draftProblem(d))
        assertTrue(SurveyLogic.draftProblem(d.copy(title = "")) != null)
        assertTrue(SurveyLogic.draftProblem(d.copy(questions = listOf(q.copy(options = emptyList())))) != null)
        assertTrue(SurveyLogic.draftProblem(d.copy(audienceScope = emptyList())) != null)
        val json = SurveyLogic.draftJson(d).toString()
        assertTrue("\"options\":[\"1학년\"]" in json)
    }
}
