package kr.co.gcflarchive.app.notice

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
import kr.co.gcflarchive.app.data.AppPrefs
import kr.co.gcflarchive.app.meal.MealNotifier
import java.util.concurrent.TimeUnit

/**
 * 새 공지 알림. 관리자가 공지를 올리며 '푸시 알림 보내기'를 고르면, 이 작업이 15분마다
 * /api/notices/pushes를 확인해 알림을 띄워요 (FCM 없이 동작하고, 배터리 절약 모드에서는 늦을 수 있어요).
 */
object NoticeNotifier {
    const val CHANNEL_ID = "notice_push"
    private const val WORK_NAME = "notice_push_check"
    private const val MAX_PER_CHECK = 3

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notice_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notice_channel_desc) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun schedule(context: Context, replace: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!AppPrefs(context).noticeNotifyEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<NoticePushWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        wm.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    @SuppressLint("MissingPermission") // checked by MealNotifier.canPost()
    fun post(context: Context, push: NoticePush) {
        if (!MealNotifier.canPost(context)) return
        // Back from the notice goes to 홈, not out of the app.
        val open = TaskStackBuilder.create(context)
            .addNextIntent(android.content.Intent(context, MainActivity::class.java))
            .addNextIntent(NoticeDetailActivity.intent(context, push.id))
            .getPendingIntent(push.id, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notice)
            .setContentTitle(context.getString(if (push.critical) R.string.notice_push_title_critical else R.string.notice_push_title))
            .setContentText(push.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(push.title))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + push.id, notification)
        } catch (_: SecurityException) {
        }
    }

    suspend fun check(context: Context) {
        val prefs = AppPrefs(context)
        if (!prefs.noticeNotifyEnabled) return
        val since = prefs.noticePushSince
        val feed = NoticeRepository.pushes(since)
        // The first check only remembers the time, so a new install isn't flooded with old notices.
        if (since != null) feed.notices.take(MAX_PER_CHECK).forEach { post(context, it) }
        prefs.noticePushSince = feed.now
    }

    private const val NOTIFICATION_BASE = 20000
}

class NoticePushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        NoticeNotifier.check(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.success() // next period tries again
    }
}
