package kr.co.gcflarchive.app.grade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuestDefaultsTest {
    @Test
    fun fromHakbun() {
        assertEquals("2" to "비영어과", GradeLogic.guestDefaults("20512"))
        assertEquals("1" to "영어과", GradeLogic.guestDefaults("10403"))
        assertEquals("3" to "비영어과", GradeLogic.guestDefaults("31015"))
        assertNull(GradeLogic.guestDefaults("61234")) // 특수 계정
        assertNull(GradeLogic.guestDefaults("21101")) // 11반: unknown
        assertNull(GradeLogic.guestDefaults("teacher"))
        assertNull(GradeLogic.guestDefaults(null))
    }
}
