package kr.co.gcflarchive.app.grade

import kr.co.gcflarchive.app.data.ApiException
import kr.co.gcflarchive.app.data.LoginRequiredException
import kr.co.gcflarchive.app.data.SiteApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Who is using the calculator (grade-calculator.html init()). */
sealed interface GradeAccess {
    data class Verified(val userId: String, val isAdmin: Boolean) : GradeAccess
    data class Guest(val userId: String, val displayName: String) : GradeAccess
    /** Logged in, but 원점수 수집·이용 (optional scope) was not agreed to yet. */
    data object NeedsScoreConsent : GradeAccess
    data object None : GradeAccess
}

class ScoreConsentRequiredException : IOException("원점수 수집·이용 동의가 필요합니다.")

data class SubmitOutcome(val guestAccessCode: String?)

data class MigrateOutcome(val moved: Int, val skipped: Int)

/** /api/grades/* (grade_calculator_backend.py). */
object GradeRepository {
    suspend fun access(): GradeAccess {
        val status = SiteApi.exchange("/api/verify-status").json
        if (status.optBoolean("verified")) {
            if (!status.optBoolean("scoreConsentGranted") || !status.optBoolean("hakbunConsentGranted")) return GradeAccess.NeedsScoreConsent
            return GradeAccess.Verified(status.optString("userId"), adminStatus())
        }
        val guest = SiteApi.exchange("/api/grades/guest-session")
        val g = guest.json
        if (guest.isSuccessful && g.optBoolean("active")) return GradeAccess.Guest(g.optString("userId"), g.optString("displayName"))
        return GradeAccess.None
    }

    private suspend fun adminStatus(): Boolean = runCatching {
        val j = SiteApi.exchange("/api/admin-status").json
        j.optBoolean("loggedIn") && j.optBoolean("isAdmin")
    }.getOrDefault(false)

    suspend fun startGuest(grade: String, track: String): GradeAccess.Guest =
        guestFrom(ok(SiteApi.exchange("/api/grades/guest-session", "POST", JSONObject().put("grade", grade).put("track", track))))

    suspend fun guestLogin(code: String): GradeAccess.Guest =
        guestFrom(ok(SiteApi.exchange("/api/grades/guest-access/login", "POST", JSONObject().put("code", code))))

    suspend fun clearGuest() {
        runCatching { SiteApi.exchange("/api/grades/guest-session", "DELETE") }
    }

    private fun guestFrom(j: JSONObject) = GradeAccess.Guest(j.optString("userId"), j.optString("displayName").ifBlank { "익명 사용자" })

    suspend fun policy(): GradePolicy {
        val res = SiteApi.exchange("/api/grades/my-entry-policy")
        if (res.code == 403 && (res.json.optBoolean("scoreConsentRequired") || res.json.optBoolean("consentRequired"))) {
            throw ScoreConsentRequiredException()
        }
        return GradeLogic.parsePolicy(ok(res))
    }

    suspend fun profile(fallback: GradePolicy?): GradeProfile =
        GradeLogic.parseProfile(ok(SiteApi.exchange("/api/grades/my-profile")), fallback)

    suspend fun submit(subject: String, score: Double, wrong: List<Int>): SubmitOutcome {
        val j = ok(SiteApi.exchange("/api/grades/submit-score", "POST", scoreBody(subject, score, wrong)))
        return SubmitOutcome(j.optString("guest_access_code").trim().ifBlank { null })
    }

    suspend fun update(subject: String, score: Double, wrong: List<Int>) {
        ok(SiteApi.exchange("/api/grades/update-my-current-score", "POST", scoreBody(subject, score, wrong)))
    }

    suspend fun delete(subject: String) {
        ok(SiteApi.exchange("/api/grades/delete-my-current-score", "POST", JSONObject().put("subject", subject)))
    }

    suspend fun cutoff(subject: String, statsMode: String): CutoffResult =
        GradeLogic.parseCutoff(ok(SiteApi.exchange("/api/grades/public-grade-cutoff/${enc(subject)}?stats_mode=$statsMode")))

    suspend fun wrongRates(subject: String, statsMode: String): Pair<List<WrongRate>, Int> {
        val j = ok(SiteApi.exchange("/api/grades/public-wrong-question-stats/${enc(subject)}?stats_mode=$statsMode"))
        return GradeLogic.parseWrongRates(j) to j.optInt("response_count")
    }

    suspend fun migrateGuestScores(): MigrateOutcome {
        val j = ok(SiteApi.exchange("/api/grades/migrate-guest-scores", "POST", JSONObject()))
        return MigrateOutcome(j.optInt("moved_count"), j.optInt("skipped_count"))
    }

    private fun scoreBody(subject: String, score: Double, wrong: List<Int>) =
        JSONObject().put("subject", subject).put("score", score).put("wrong_questions", JSONArray(wrong))

    private fun enc(s: String) = android.net.Uri.encode(s)

    /** Body of a successful call; otherwise the server's own message. */
    private fun ok(res: SiteApi.Response): JSONObject {
        if (res.code == 401) throw LoginRequiredException(res.error ?: "로그인이 필요합니다.")
        if (!res.isSuccessful || res.json.has("success") && !res.json.optBoolean("success")) {
            throw ApiException(res.code, res.error ?: "요청을 처리하지 못했습니다. (HTTP ${res.code})")
        }
        return res.json
    }
}
