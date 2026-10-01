package kr.co.gcflarchive.app.share

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DriveLogicTest {
    private val json = JSONObject(
        """
        {"pin":"1234","files":[
          {"file_id":"a","filename":"a.pdf","file_size":3145728,"uploaded_at":"2026-10-01T00:00:00.123456Z",
           "expires_at":"2026-10-08T00:00:00.123456Z","short_code":"ABC123","downloaded_at":null,"delete_at":null,"quota_exempt":false},
          {"file_id":"b","filename":"b.hwp","file_size":2097152,"uploaded_at":"2026-10-01T00:00:00Z",
           "expires_at":"2026-10-08T00:00:00Z","short_code":"DEF456","downloaded_at":"2026-10-01T01:00:00Z","delete_at":"2026-10-01T01:05:00Z","quota_exempt":false},
          {"file_id":"c","filename":"exam.pdf","file_size":9000000,"uploaded_at":"2026-10-01T00:00:00Z",
           "expires_at":"2026-10-08T00:00:00Z","short_code":"GHI789","quota_exempt":true}
        ]}
        """,
    )

    @Test
    fun usageIgnoresExamCopies() {
        val listing = DriveLogic.parseListing(json)
        assertEquals("1234", listing.pin)
        val usage = DriveLogic.usage(listing.files)
        assertEquals(2, usage.count)
        assertEquals(5L * 1024 * 1024, usage.bytes)
        assertEquals(50, usage.percent)
        assertFalse(usage.isFull)
    }

    @Test
    fun statusMatchesSharePage() {
        val files = DriveLogic.parseListing(json).files
        val now = Instant.parse("2026-10-01T01:02:30Z")
        assertEquals(DriveFileStatus.Stored(7), DriveLogic.status(files[0], now))
        assertEquals(DriveFileStatus.Downloaded(3), DriveLogic.status(files[1], now))
        assertEquals(DriveFileStatus.Downloaded(0), DriveLogic.status(files[1], Instant.parse("2026-10-02T00:00:00Z")))
    }

    @Test
    fun pinAndUrl() {
        assertTrue(DriveLogic.isValidPin("0420"))
        assertFalse(DriveLogic.isValidPin("12a4"))
        assertFalse(DriveLogic.isValidPin("123"))
        assertEquals("https://gcflarchive.co.kr/20315-0420", DriveLogic.driveUrl("20315", "0420"))
    }
}
