package kr.co.gcflarchive.admin.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.NotifyKind
import kr.co.gcflarchive.admin.ui.MainActivity

object AdminNotifier {
    private fun channelId(kind: NotifyKind) = "admin_${kind.name.lowercase()}"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        NotifyKind.entries.forEach { k ->
            val importance = if (k == NotifyKind.AUTO_BLOCK || k == NotifyKind.ADMIN_CHANGE) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            nm.createNotificationChannel(NotificationChannel(channelId(k), k.label, importance).apply { description = k.description })
        }
    }

    /**
     * Posts a notification whose tap opens [routeKey] (딥링크). Content is kept generic on
     * the lock screen (VISIBILITY_PRIVATE) because it can include IPs and hakbuns.
     */
    @SuppressLint("MissingPermission") // checked below
    fun post(context: Context, kind: NotifyKind, id: Int, title: String, text: String, routeKey: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, id, MainActivity.intent(context, routeKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val public = NotificationCompat.Builder(context, channelId(kind))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(kind.label)
            .setContentText(context.getString(R.string.notify_public_text))
            .build()
        val n = NotificationCompat.Builder(context, channelId(kind))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
