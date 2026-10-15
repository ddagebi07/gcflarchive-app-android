package kr.co.gcflarchive.app.survey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyLogicTest {
    private val formJson = """{"id":3,"title":"축제 설문","status":"open","statusLabel":"응답 받는 중","responded":true,
        "questions":[{"id":"s","type":"section","title":"기본"},
                     {"id":"grade","type":"choice","title":"학년","options":["1학년","2학년"],"required":true},
                     {"id":"clubs","type":"checkbox","title":"동아리","options":["밴드"],"other":true},
                     {"id":"score","type":"scale","title":"만족도","min":1,"max":5},
                     {"id":"day","type":"date","title":"날짜"}],
        "myAnswers":{"grade":"2학년","clubs":["밴드","로봇"],"score":4},"canRespond":true,"accepting":true}"""

    @Test
    fun parsesFormAndAnswers() {
        val f = SurveyLogic.parseForm(formJson)
        assertEquals(5, f.questions.size)
        assertEquals(Answer.Picks(listOf("밴드", "로봇")), f.myAnswers!!["clubs"])
        assertEquals(Answer.Number(4), f.myAnswers!!["score"])
        assertNull(f.summary.responseCount)
        assertEquals("grade", SurveyLogic.firstProblem(f.questions, emptyMap())?.first)
        assertEquals("day", SurveyLogic.firstProblem(f.questions, mapOf("grade" to Answer.Text("1학년"), "day" to Answer.Text("x")))?.first)
    }

    @Test
    fun parsesResultsAndBuildsJson() {
        val r = SurveyLogic.parseResults("""{"title":"t","responseCount":1,"summary":[{"title":"학년","answered":1,"options":[{"text":"1학년","count":1}]},
            {"title":"만족도","answered":1,"scale":[{"value":1,"count":1}],"average":1.0}],
            "questions":[{"id":"grade","title":"학년"}],"responses":[{"respondent":"","submittedAt":"x","answers":{"clubs":["a","b"],"score":3}}]}""")
        assertEquals(1, r.summary[0].options[0].count)
        assertEquals(1.0, r.summary[1].average!!, 0.0)
        assertEquals("a, b", r.responses[0].answers["clubs"])
        assertEquals("3", r.responses[0].answers["score"])
        val d = SurveyDraft("설문", "", listOf("login"), "open", "", false, false, false,
            listOf(SurveyQuestion("q1", QuestionType.CHOICE, "학년", options = listOf("1학년", " "))))
        assertNull(SurveyLogic.draftProblem(d))
        assertTrue(SurveyLogic.draftProblem(d.copy(title = "")) != null)
        assertEquals(1, SurveyLogic.draftJson(d).getJSONArray("questions").getJSONObject(0).getJSONArray("options").length())
        assertEquals(3, SurveyLogic.answersJson(mapOf("a" to Answer.Number(3))).getInt("a"))
    }
}
