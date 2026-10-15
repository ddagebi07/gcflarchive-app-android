package kr.co.gcflarchive.app.school

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.meal.MealNotifier
import kr.co.gcflarchive.student.school.SchoolActivity
import java.util.concurrent.TimeUnit

/**
 * 학교생활 알림 (연락, 질문 답변, 보강 부탁, 다음 교시 예고).
 * - FCM이 설정돼 있으면 SchoolPushService가 바로 받아요.
 * - 아니면 이 작업이 15분마다 서버 알림함(/api/notifications)을 확인해 띄워요.
 * 채널은 셋이라 '다음 교시 예고'만 따로 끌 수 있어요.
 */
object SchoolNotifier {
    const val CHANNEL_SCHOOL = "school"
    const val CHANNEL_SUBSTITUTE = "substitute"
    const val CHANNEL_NEXT_PERIOD = "next_period"
    private const val WORK_NAME = "school_alert_check"
    private const val NOTIFICATION_BASE = 40000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            Triple(CHANNEL_SCHOOL, R.string.school_channel_name, R.string.school_channel_desc),
            Triple(CHANNEL_SUBSTITUTE, R.string.substitute_channel_name, R.string.substitute_channel_desc),
            Triple(CHANNEL_NEXT_PERIOD, R.string.next_period_channel_name, R.string.next_period_channel_desc),
        ).forEach { (id, name, desc) ->
            nm.createNotificationChannel(
                NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_HIGH).apply { description = context.getString(desc) },
            )
        }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SchoolAlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun channelFor(kind: String): String = when (kind) {
        "substitute" -> CHANNEL_SUBSTITUTE
        "next_period" -> CHANNEL_NEXT_PERIOD
        else -> CHANNEL_SCHOOL
    }

    @SuppressLint("MissingPermission") // checked by MealNotifier.canPost()
    fun post(context: Context, id: Int, kind: String, title: String, body: String, target: String) {
        if (!MealNotifier.canPost(context)) return
        val open = TaskStackBuilder.create(context)
            .addNextIntent(Intent(context, MainActivity::class.java))
            .addNextIntent(SchoolActivity.intent(context, target = target.ifBlank { null }))
            .getPendingIntent(NOTIFICATION_BASE + id, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, channelFor(kind))
            .setSmallIcon(R.drawable.ic_notice)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + id, n)
        } catch (_: SecurityException) {
        }
    }

    /** Registers this device and shows inbox alerts when there is no FCM token. */
    suspend fun check(context: Context) {
        val services = SchoolActivity.services(context)
        if (runCatching { services.school.me() }.getOrNull()?.loggedIn != true) return
        runCatching { services.school.registerThisDevice(services.prefs, "android") }
        val alerts = runCatching { services.school.newAlerts(services.prefs) }.getOrDefault(emptyList())
        alerts.forEach { post(context, it.id, it.kind, it.title, it.body, it.target) }
    }
}

class SchoolAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { SchoolNotifier.check(applicationContext) }
        return Result.success()
    }
}
