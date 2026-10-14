package kr.co.gcflarchive.core.grade

import kr.co.gcflarchive.core.encPath
import kr.co.gcflarchive.core.net.ApiException
import kr.co.gcflarchive.core.net.JObject
import kr.co.gcflarchive.core.net.LoginRequiredException
import kr.co.gcflarchive.core.net.SiteClient
import kr.co.gcflarchive.core.net.jsonOf

/** Who is using the calculator (grade-calculator.html init()). */
sealed interface GradeAccess {
    data class Verified(val userId: String, val isAdmin: Boolean) : GradeAccess
    /** [hakbun] set: anonymous mode chosen by that logged-in student (site login kept). */
    data class Guest(val userId: String, val displayName: String, val hakbun: String? = null) : GradeAccess {
        val keepsLogin: Boolean get() = hakbun != null
    }
    /** Logged in as [userId], but 원점수 수집·이용 (optional scope) was not agreed to yet. */
    data class NeedsScoreConsent(val userId: String) : GradeAccess
    data object None : GradeAccess
}

class ScoreConsentRequiredException : Exception("원점수 수집·이용 동의가 필요해요.")

data class SubmitOutcome(val guestAccessCode: String?)

data class MigrateOutcome(val moved: Int, val skipped: Int)

/** /api/grades/… (grade_calculator_backend.py). */
class GradeRepository(private val site: SiteClient) {
    suspend fun access(): GradeAccess {
        val status = site.exchange("/api/verify-status").json
        val verified = status.optBoolean("verified")
        val guest = site.exchange("/api/grades/guest-session")
        val g = guest.json
        // A guest session counts when logged out, or when a logged-in student chose it ("preferred").
        if (guest.isSuccessful && g.optBoolean("active") && (!verified || g.optBoolean("preferred"))) {
            return GradeAccess.Guest(
                g.optString("userId"),
                g.optString("displayName").ifBlank { "익명 사용자" },
                hakbun = status.optString("userId").takeIf { verified && it.isNotBlank() },
            )
        }
        if (verified) {
            val id = status.optString("userId")
            if (!status.optBoolean("scoreConsentGranted") || !status.optBoolean("hakbunConsentGranted")) return GradeAccess.NeedsScoreConsent(id)
            return GradeAccess.Verified(id, adminStatus())
        }
        return GradeAccess.None
    }

    private suspend fun adminStatus(): Boolean = runCatching {
        val j = site.exchange("/api/admin-status").json
        j.optBoolean("loggedIn") && j.optBoolean("isAdmin")
    }.getOrDefault(false)

    /** [keepLogin]: a logged-in student goes anonymous without being signed out of the site. */
    suspend fun startGuest(grade: String, track: String, keepLogin: Boolean = false): GradeAccess.Guest =
        guestFrom(ok(site.exchange("/api/grades/guest-session", "POST", jsonOf("grade" to grade, "track" to track, "preferGuest" to keepLogin))))

    suspend fun guestLogin(code: String, keepLogin: Boolean = false): GradeAccess.Guest =
        guestFrom(ok(site.exchange("/api/grades/guest-access/login", "POST", jsonOf("code" to code, "preferGuest" to keepLogin))))

    suspend fun clearGuest() {
        runCatching { site.exchange("/api/grades/guest-session", "DELETE") }
    }

    private fun guestFrom(j: JObject) = GradeAccess.Guest(j.optString("userId"), j.optString("displayName").ifBlank { "익명 사용자" })

    suspend fun policy(): GradePolicy {
        val res = site.exchange("/api/grades/my-entry-policy")
        if (res.code == 403 && (res.json.optBoolean("scoreConsentRequired") || res.json.optBoolean("consentRequired"))) {
            throw ScoreConsentRequiredException()
        }
        return GradeLogic.parsePolicy(ok(res))
    }

    suspend fun profile(fallback: GradePolicy?): GradeProfile =
        GradeLogic.parseProfile(ok(site.exchange("/api/grades/my-profile")), fallback)

    suspend fun submit(subject: String, score: Double, wrong: List<Int>): SubmitOutcome {
        val j = ok(site.exchange("/api/grades/submit-score", "POST", scoreBody(subject, score, wrong)))
        return SubmitOutcome(j.optString("guest_access_code").trim().ifBlank { null })
    }

    suspend fun update(subject: String, score: Double, wrong: List<Int>) {
        ok(site.exchange("/api/grades/update-my-current-score", "POST", scoreBody(subject, score, wrong)))
    }

    suspend fun delete(subject: String) {
        ok(site.exchange("/api/grades/delete-my-current-score", "POST", jsonOf("subject" to subject)))
    }

    suspend fun cutoff(subject: String, statsMode: String): CutoffResult =
        GradeLogic.parseCutoff(ok(site.exchange("/api/grades/public-grade-cutoff/${enc(subject)}?stats_mode=$statsMode")))

    suspend fun wrongRates(subject: String, statsMode: String): Pair<List<WrongRate>, Int> {
        val j = ok(site.exchange("/api/grades/public-wrong-question-stats/${enc(subject)}?stats_mode=$statsMode"))
        return GradeLogic.parseWrongRates(j) to j.optInt("response_count")
    }

    suspend fun migrateGuestScores(): MigrateOutcome {
        val j = ok(site.exchange("/api/grades/migrate-guest-scores", "POST", jsonOf()))
        return MigrateOutcome(j.optInt("moved_count"), j.optInt("skipped_count"))
    }

    private fun scoreBody(subject: String, score: Double, wrong: List<Int>) =
        jsonOf("subject" to subject, "score" to score, "wrong_questions" to wrong)

    private fun enc(s: String) = encPath(s)

    /** Body of a successful call; otherwise the server's own message. */
    private fun ok(res: SiteClient.Response): JObject {
        if (res.code == 401) throw LoginRequiredException(res.error ?: "로그인하면 이용할 수 있어요.")
        if (!res.isSuccessful || res.json.has("success") && !res.json.optBoolean("success")) {
            throw ApiException(res.code, res.error ?: "처리하지 못했어요. 잠시 후 다시 시도해주세요.")
        }
        return res.json
    }
}
