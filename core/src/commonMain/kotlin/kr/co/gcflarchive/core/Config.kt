package kr.co.gcflarchive.core

import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart

object Config {
    const val BASE_URL = "https://gcflarchive.co.kr"
    const val HOST = "gcflarchive.co.kr"

    /** Appended to the user agent so the server can tell app traffic apart. */
    const val USER_AGENT = "GCFLArchiveApp/2.0"

    // Same values meal.html uses for the NEIS open API.
    const val NEIS_API_KEY = "2087d61d2897469dbd2619d96224bbd0"
    const val NEIS_OFFICE_CODE = "J10"      // 경기도교육청
    const val NEIS_SCHOOL_CODE = "7530145"  // 과천외국어고등학교
    const val SCHOOL_NAME = "과천외국어고등학교"

    /** 극플드라이브: up to 30MB of your own files, any type (routes/temp_share.py DRIVE_QUOTA_MB). */
    const val DRIVE_MAX_TOTAL_BYTES = 30L * 1024 * 1024

    /** School location the map starts at (map.html). */
    const val SCHOOL_LAT = 37.4313541
    const val SCHOOL_LNG = 126.9892723

    fun url(path: String): String = if (path.startsWith("http")) path else BASE_URL + path

    fun isOwnHost(url: String): Boolean {
        val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
        return host == HOST || host.endsWith(".$HOST")
    }
}

/** Path segment / query value encoding shared by the API clients. */
fun encPath(segment: String): String = segment.encodeURLPathPart()
fun encQuery(value: String): String = value.encodeURLParameter()
