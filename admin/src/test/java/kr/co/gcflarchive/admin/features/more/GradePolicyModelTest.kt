package kr.co.gcflarchive.admin.features.more

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradePolicyModelTest {
    private val json = JSONObject(
        """
        {"current_exam_name":"2학기 중간",
         "subject_mapping":{
           "rules":[{"grade":"1","class_start":"1","class_end":"4","subjects":["국어","영어","물리","화학"]},
                    {"grade":"","class_start":"","class_end":"","subjects":[]}],
           "selection_groups":[{"label":"과학 선택","slot_subject":"과학","subjects":["물리","화학"]}],
           "subject_submit_after":{"국어":"2026-10-14T00:00:00+00:00"},
           "subject_question_counts":{"국어":25},
           "subject_descriptive_question_counts":{"국어":3}}}
        """,
    )

    @Test
    fun parseDropsEmptyRulesAndListsSubjects() {
        val p = ExamPolicyCodec.parse(json)
        assertEquals("2학기 중간", p.examName)
        assertEquals(1, p.rules.size)
        assertEquals(listOf("국어", "영어", "물리", "화학", "과학"), ExamPolicyCodec.subjects(p))
        assertEquals("1학년 1~4반", ExamPolicyCodec.describeTarget(p.rules[0]))
        assertEquals("전체 학년 전체 반", ExamPolicyCodec.describeTarget(SubjectRule("", "", "", listOf("x"))))
    }

    @Test
    fun payloadRoundTripsAndDropsZeroCounts() {
        val p = ExamPolicyCodec.parse(json)
        val edited = p.copy(questionCounts = p.questionCounts + ("영어" to 0), submitAfter = p.submitAfter - "국어")
        val payload = ExamPolicyCodec.toPayload(edited)
        val m = payload.getJSONObject("subject_mapping")
        assertTrue(m.getJSONObject("subject_submit_after").length() == 0)
        assertTrue(!m.getJSONObject("subject_question_counts").has("영어"))
        assertEquals(p.copy(submitAfter = emptyMap()), ExamPolicyCodec.parse(payload))
    }

    @Test
    fun ruleValidation() {
        assertNotNull(ExamPolicyCodec.validateRule(SubjectRule("1", "", "", emptyList())))
        assertNotNull(ExamPolicyCodec.validateRule(SubjectRule("1", "5", "2", listOf("국어"))))
        assertNotNull(ExamPolicyCodec.validateRule(SubjectRule("1", "a", "", listOf("국어"))))
        assertNull(ExamPolicyCodec.validateRule(SubjectRule("1", "5", "10", listOf("국어"))))
        assertNull(ExamPolicyCodec.validateRule(SubjectRule("", "", "", listOf("국어"))))
    }

    @Test
    fun groupValidationRejectsSharedSubjects() {
        val groups = ExamPolicyCodec.parse(json).groups
        assertNotNull(ExamPolicyCodec.validateGroup(groups, null, SelectionGroup("", "", listOf("물리", "지구과학"))))
        assertNull(ExamPolicyCodec.validateGroup(groups, 0, SelectionGroup("과학", "", listOf("물리", "지구과학"))))
        assertNotNull(ExamPolicyCodec.validateGroup(groups, null, SelectionGroup("", "", listOf("한문"))))
    }

    @Test
    fun moveAndCounts() {
        assertEquals(listOf("b", "a", "c"), ExamPolicyCodec.move(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("c", "a", "b"), ExamPolicyCodec.move(listOf("a", "b", "c"), 2, 0))
        assertNotNull(ExamPolicyCodec.validateCounts(10, 11))
        assertNull(ExamPolicyCodec.validateCounts(10, 10))
    }

    @Test
    fun manualCutoffs() {
        val ok = ManualCutoffs.build("3", listOf("95", "90.5", "", "80")).getOrThrow()
        assertEquals(9, ok.length())
        assertEquals(90.5, ok.getJSONObject(1).getDouble("cutoff_score"), 0.0)
        assertTrue(ok.getJSONObject(2).isNull("cutoff_score"))
        assertEquals("3", ok.getJSONObject(0).getString("grade_level"))
        assertEquals("2등급 컷이 위 등급보다 높습니다.", ManualCutoffs.build("1", listOf("80", "90")).exceptionOrNull()?.message)
        assertNotNull(ManualCutoffs.build("1", listOf("101")).exceptionOrNull())
        assertNotNull(ManualCutoffs.build("1", listOf("", "")).exceptionOrNull())

        val rows = listOf(
            JSONObject("""{"grade":1,"grade_level":"2","cutoff_score":92.0}"""),
            JSONObject("""{"grade":2,"grade_level":"2","cutoff_score":null}"""),
            JSONObject("""{"grade":1,"grade_level":"1","cutoff_score":70}"""),
        )
        assertEquals("2", ManualCutoffs.levelOf(rows))
        assertEquals(listOf("92", "", "", "", ""), ManualCutoffs.prefill(rows, "2"))
    }
}
