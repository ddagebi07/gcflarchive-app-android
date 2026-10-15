package kr.co.gcflarchive.admin.update

import org.json.JSONObject

/** A build listed on the site's beta page (/api/beta/latest). */
data class LatestBuild(val versionName: String, val versionCode: Int?, val notes: String, val page: String)

/** Pure update decisions (unit-tested). */
object UpdateLogic {
    fun parse(body: String): LatestBuild? {
        val latest = runCatching { JSONObject(body).optJSONObject("latest") }.getOrNull() ?: return null
        val name = latest.optString("versionName").trim()
        if (name.isEmpty()) return null
        return LatestBuild(
            versionName = name,
            versionCode = latest.optString("versionCode").trim().toIntOrNull(),
            notes = latest.optString("notes").trim(),
            page = latest.optString("page").ifBlank { "/beta" },
        )
    }

    /** "1.10.2" -> [1, 10, 2]; anything that isn't a number is ignored. */
    fun numbers(version: String): List<Int> = Regex("\\d+").findAll(version).map { it.value.toInt() }.toList()

    /** Newer by versionCode when the build has one, otherwise by the numbers in the version name. */
    fun isNewer(latest: LatestBuild, currentName: String, currentCode: Int): Boolean {
        latest.versionCode?.let { return it > currentCode }
        val a = numbers(latest.versionName)
        val b = numbers(currentName)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Ask unless the user already chose 나중에 for exactly this version. */
    fun shouldPrompt(latest: LatestBuild?, currentName: String, currentCode: Int, skipped: String?): Boolean =
        latest != null && isNewer(latest, currentName, currentCode) && latest.versionName != skipped
}
