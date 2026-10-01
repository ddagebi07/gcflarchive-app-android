package kr.co.gcflarchive.app.grade

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GradeLogicTest {
    private val now = Instant.parse("2026-10-01T03:00:00Z")

    private val policyJson = JSONObject(
        """
        {"current_exam_name":"2학기 중간","allowed_subjects":["국어","수학","물리","화학","영어"],
         "selection_groups":[{"label":"과학 선택","slot_subject":"과학","subjects":["물리","화학"]},
                             {"label":"중복","subjects":["물리"]}],
         "subject_submit_after":{"수학":"2026-10-01T05:00:00Z","국어":"2026-10-01T02:00:00Z","과학":"2026-10-01T04:00:00Z"},
         "subject_question_counts":{"국어":5},
         "subject_descriptive_question_counts":{"국어":2},
         "server_time":"2026-10-01T03:00:30Z"}
        """,
    )

    @Test
    fun parsesPolicyAndClockOffset() {
        val p = GradeLogic.parsePolicy(policyJson, now)
        assertEquals("2학기 중간", p.examName)
        assertEquals(1, p.selectionGroups.size) // single-subject group dropped
        assertEquals(30_000L, p.clockOffsetMillis)
    }

    @Test
    fun inputRowsSkipSelectionSubjectsAndSortByOpening() {
        val p = GradeLogic.parsePolicy(policyJson, now)
        val rows = GradeLogic.inputRows(p)
        assertEquals(listOf("국어", "수학", "영어"), rows.filterIsInstance<InputRow.Fixed>().map { it.subject })
        val group = rows.filterIsInstance<InputRow.Group>().single()
        assertEquals("과학", group.scheduleSubject)
    }

    @Test
    fun submitWindowUsesServerClock() {
        val p = GradeLogic.parsePolicy(policyJson, now)
        assertTrue(GradeLogic.openInfo("국어", p, now).open)
        assertFalse(GradeLogic.openInfo("수학", p, now).open)
        assertTrue(GradeLogic.openInfo("영어", p, now).open) // no time set
        assertFalse(GradeLogic.openInfo("", p, now).open)
    }

    @Test
    fun descriptiveQuestionLabels() {
        val p = GradeLogic.parsePolicy(policyJson, now)
        assertEquals(listOf("1", "2", "3", "서술형1", "서술형2"), (1..5).map { GradeLogic.questionLabel("국어", it, p) })
    }

    @Test
    fun scoreValidation() {
        assertEquals(87.5, GradeLogic.parseScore(" 87.5 ").getOrThrow(), 0.0)
        assertEquals("점수를 입력하세요.", GradeLogic.parseScore("").exceptionOrNull()?.message)
        assertEquals("점수는 0~100 사이여야 합니다.", GradeLogic.parseScore("101").exceptionOrNull()?.message)
    }

    @Test
    fun myGradeIsBestReachedCutoff() {
        val rows = listOf(CutoffRow(1, 95.0), CutoffRow(2, 88.0), CutoffRow(3, null), CutoffRow(4, 70.0))
        assertEquals(2, GradeLogic.myGrade(rows, 90.0))
        assertEquals(4, GradeLogic.myGrade(rows, 70.0))
        assertNull(GradeLogic.myGrade(rows, 50.0))
        assertNull(GradeLogic.myGrade(rows, null))
    }

    @Test
    fun profileParsing() {
        val json = JSONObject(
            """
            {"current_exam_scores":{"국어":{"score":91,"wrong_questions":[3,1,3]},"수학":{"score":null}},
             "scores":[{"subject":"국어","exam_name":"중간","score":91.0,"percentile":null,"grade":2,"timestamp":"2026-09-01"},
                       {"subject":"영어","exam_name":"기말","score":80.5,"timestamp":"2026-09-02"}]}
            """,
        )
        val prof = GradeLogic.parseProfile(json, GradeLogic.parsePolicy(policyJson, now), now)
        assertEquals(setOf("국어"), prof.current.keys)
        assertEquals(listOf(1, 3), prof.current["국어"]!!.wrongQuestions)
        assertEquals(listOf("영어", "국어"), prof.scores.map { it.subject }) // newest first
        assertEquals("91", prof.scores[1].score)
        assertEquals("-", prof.scores[1].percentile)
        assertEquals(listOf("국어", "영어"), prof.submittedSubjects.sorted())
        assertEquals("2학기 중간", prof.policy.examName) // falls back to the policy
    }

    @Test
    fun wrongRateSorting() {
        val rows = listOf(WrongRate(1, 10.0, 1, 10), WrongRate(2, 50.0, 5, 10), WrongRate(3, 50.0, 5, 10))
        assertEquals(listOf(2, 3, 1), GradeLogic.sortWrongRates(rows, WrongRateSort.RATE_DESC).map { it.questionNo })
        assertEquals(listOf(3, 2, 1), GradeLogic.sortWrongRates(rows, WrongRateSort.QUESTION_DESC).map { it.questionNo })
    }

    @Test
    fun testUserRange() {
        assertTrue(GradeLogic.isTestUser("60000"))
        assertFalse(GradeLogic.isTestUser("20315"))
        assertFalse(GradeLogic.isTestUser(null))
    }
}
