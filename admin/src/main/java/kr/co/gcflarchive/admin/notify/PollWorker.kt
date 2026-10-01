package kr.co.gcflarchive.admin.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminApi
import kr.co.gcflarchive.admin.core.AdminPrefs
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.core.IpBand
import kr.co.gcflarchive.admin.core.NotifyKind
import kr.co.gcflarchive.admin.core.Permission
import kr.co.gcflarchive.admin.features.objects
import kr.co.gcflarchive.admin.features.str
import kr.co.gcflarchive.admin.features.strings
import kr.co.gcflarchive.admin.ui.Route
import kr.co.gcflarchive.admin.widget.StatusWidget
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Admin alerts without a push server: every 15 minutes (WorkManager's minimum) the
 * worker compares the current state with the last snapshot and notifies on changes:
 * 자동 IP 차단, 지도 요청·리뷰 접수, 점검 모드 변경, 관리자 계정 변경. The first run only
 * records a baseline. (Real FCM push needs a Firebase project + server endpoints.)
 */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val session = AdminSession.get(ctx)
        if (!session.isSignedIn) return Result.success()
        val api = AdminApi.get(ctx)
        val prefs = AdminPrefs(ctx)
        val g = session.grants

        suspend fun json(path: String) = runCatching { api.get(path).let { if (it.fromCache) null else it.json() } }.getOrNull()

        /** Calls [onNew] with items whose keys weren't in the previous snapshot. */
        fun diff(key: String, current: Set<String>, onNew: (Set<String>) -> Unit) {
            val prev = prefs.snapshot(key)?.let { s -> JSONArray(s).strings().toSet() }
            prefs.setSnapshot(key, JSONArray(current.toList()).toString())
            if (prev != null) (current - prev).takeIf { it.isNotEmpty() }?.let(onNew)
        }

        // 자동 IP 차단
        if (g.has(Permission.BLOCKED_IPS)) {
            // Only diff on a fresh response: a failed fetch must not reset the baseline.
            json("/api/blocked-ips")?.let { j ->
                val list = j.optJSONArray("blocked").objects()
                prefs.setSnapshot("widget_blocked", list.size.toString())
                val auto = list.filter { IpBand.isAuto(it.str("reason")) }.map { it.str("band") }.toSet()
                diff("auto_blocks", auto) { new ->
                    if (prefs.notifyEnabled(NotifyKind.AUTO_BLOCK)) {
                        AdminNotifier.post(ctx, NotifyKind.AUTO_BLOCK, 101, ctx.getString(R.string.notify_auto_block_title, new.size), new.joinToString("\n"), Route.IP_BLOCKS.key)
                    }
                }
            }
        }

        // 지도 요청·리뷰
        if (g.canModerateMap()) {
            json("/api/admin/map/pending-items")?.let { p ->
                val ids = p.optJSONArray("pendingPlaces").objects().map { "p:" + it.str("placeId") } +
                    p.optJSONArray("pendingDetails").objects().map { "d:" + it.str("requestId") }
                diff("map_pending", ids.toSet()) { new ->
                    if (prefs.notifyEnabled(NotifyKind.MAP_REQUEST)) {
                        AdminNotifier.post(ctx, NotifyKind.MAP_REQUEST, 102, ctx.getString(R.string.notify_map_title, new.size), ctx.getString(R.string.notify_map_text), Route.MAP_REQUESTS.key)
                    }
                }
            }
            runCatching { api.get("/api/admin/map/reviews").takeIf { !it.fromCache }?.let { JSONArray(it.body) } }.getOrNull()?.objects()?.let { reviews ->
                diff("map_reviews", reviews.map { it.str("reviewId") }.toSet()) { new ->
                    if (prefs.notifyEnabled(NotifyKind.MAP_REQUEST)) {
                        AdminNotifier.post(ctx, NotifyKind.MAP_REQUEST, 103, ctx.getString(R.string.notify_review_title, new.size), ctx.getString(R.string.notify_review_text), Route.MAP_REVIEWS.key)
                    }
                }
            }
        }

        // 점검 모드 변경 (public endpoint)
        json("/api/maintenance")?.optBoolean("enabled")?.let { on ->
            val prev = prefs.snapshot("maintenance")
            prefs.setSnapshot("maintenance", on.toString())
            prefs.setSnapshot("widget_maintenance", on.toString())
            if (prev != null && prev != on.toString() && prefs.notifyEnabled(NotifyKind.MAINTENANCE)) {
                AdminNotifier.post(ctx, NotifyKind.MAINTENANCE, 104, ctx.getString(if (on) R.string.notify_maint_on else R.string.notify_maint_off), ctx.getString(R.string.notify_maint_text), "home")
            }
        }

        // 관리자 계정 변경
        if (g.has(Permission.CREDENTIALS)) {
            json("/api/admins")?.let { j ->
                val admins = j.optJSONArray("admins").objects()
                val sig = admins.map { it.str("hakbun") + "=" + it.optJSONArray("permissions").strings().sorted().joinToString("|") }.sorted().joinToString(";")
                val prev = prefs.snapshot("admins")
                prefs.setSnapshot("admins", sig)
                if (prev != null && prev != sig && prefs.notifyEnabled(NotifyKind.ADMIN_CHANGE)) {
                    AdminNotifier.post(ctx, NotifyKind.ADMIN_CHANGE, 105, ctx.getString(R.string.notify_admin_title), ctx.getString(R.string.notify_admin_text, admins.size), Route.ADMINS.key)
                }
            }
        }

        StatusWidget.refresh(ctx)
        return Result.success()
    }

    companion object {
        private const val NAME = "admin_poll"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
