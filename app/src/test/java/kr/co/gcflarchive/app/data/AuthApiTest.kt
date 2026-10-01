package kr.co.gcflarchive.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthApiTest {
    @Test
    fun customAccountRangeMatchesServer() {
        assertTrue(AuthApi.isCustomAccount("60000"))
        assertTrue(AuthApi.isCustomAccount("99999"))
        assertFalse(AuthApi.isCustomAccount("59999"))
        assertFalse(AuthApi.isCustomAccount("10101"))
        assertFalse(AuthApi.isCustomAccount("6000a"))
    }

    @Test
    fun endpointFollowsVerifyPage() {
        assertEquals("/api/verify-login", AuthApi.endpointFor(AuthApi.Mode.STUDENT, "20315"))
        assertEquals("/api/login-custom-account", AuthApi.endpointFor(AuthApi.Mode.STUDENT, "70001"))
        assertEquals("/api/teacher-login", AuthApi.endpointFor(AuthApi.Mode.TEACHER, "70001"))
    }
}
