package kr.co.gcflarchive.app.meal

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object MealNotifier {
    const val CHANNEL_ID = "meal_daily"
    private const val WORK_NAME = "meal_daily_notification"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.meal_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.meal_channel_desc) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * Schedules the daily check at the user's chosen time. [replace] is true when the
     * user changed settings; at app start we keep the existing schedule so the initial
     * delay doesn't get reset on every launch.
     */
    fun schedule(context: Context, replace: Boolean) {
        val prefs = AppPrefs(context)
        val wm = WorkManager.getInstance(context)
        if (!prefs.mealNotifyEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<MealNotifyWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(prefs.mealNotifyMinuteOfDay).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        wm.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    private fun delayUntil(minuteOfDay: Int): Duration {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target)
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Returns false when there was nothing to show (no meal service that day). */
    @SuppressLint("MissingPermission") // checked by canPost()
    fun post(context: Context, meals: List<Meal>, kind: AppPrefs.MealKind): Boolean {
        val selected = kind.neisName?.let { name -> meals.filter { it.kind == name } } ?: meals
        if (selected.isEmpty() || !canPost(context)) return false

        val title = if (selected.size == 1) {
            context.getString(R.string.meal_notify_title_one, selected[0].kind)
        } else {
            context.getString(R.string.meal_notify_title_all)
        }
        val summary = selected.joinToString("  |  ") { meal ->
            "${meal.kind}: " + meal.dishes.take(3).joinToString(", ") { it.name }
        }
        val full = selected.joinToString("\n\n") { meal ->
            "[${meal.kind}]" + (meal.calories?.let { " $it" } ?: "") + "\n" +
                meal.dishes.joinToString("\n") { "· ${it.name}" }
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_MEAL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_meal)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(full))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            return false
        }
        return true
    }
}

class MealNotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = AppPrefs(applicationContext)
        if (!prefs.mealNotifyEnabled) return Result.success()
        val today = MealRepository.today()
        val key = today.format(MealRepository.NEIS_DATE)
        if (prefs.lastNotifiedDate == key) return Result.success()
        val meals = try {
            MealRepository.fetch(today)
        } catch (_: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }
        if (MealNotifier.post(applicationContext, meals, prefs.mealNotifyKind)) {
            prefs.lastNotifiedDate = key
        }
        return Result.success()
    }
}
