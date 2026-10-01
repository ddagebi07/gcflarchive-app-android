package kr.co.gcflarchive.admin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminPrefs
import kr.co.gcflarchive.admin.ui.LoginActivity

/**
 * 홈 화면 위젯: 점검 모드 상태와 차단 대역 수를 보여 주고, 버튼으로 해당 화면을 엽니다.
 * The widget never changes settings itself — toggling goes through the app so the
 * biometric re-lock applies. Values come from the last background poll.
 */
class StatusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val prefs = AdminPrefs(context)
            val maint = prefs.snapshot("widget_maintenance")
            val blocked = prefs.snapshot("widget_blocked")
            return RemoteViews(context.packageName, R.layout.widget_status).apply {
                setTextViewText(R.id.widgetMaintenance, when (maint) {
                    "true" -> context.getString(R.string.widget_maint_on)
                    "false" -> context.getString(R.string.widget_maint_off)
                    else -> context.getString(R.string.widget_unknown)
                })
                setTextViewText(R.id.widgetBlocked, blocked?.let { context.getString(R.string.widget_blocked, it) } ?: context.getString(R.string.widget_unknown))
                setOnClickPendingIntent(R.id.widgetMaintenanceButton, open(context, "maintenance", 1))
                setOnClickPendingIntent(R.id.widgetBlockedButton, open(context, "ip_blocks", 2))
            }
        }

        private fun open(context: Context, route: String, code: Int): PendingIntent =
            PendingIntent.getActivity(
                context, code,
                Intent(Intent.ACTION_VIEW, Uri.parse("gcfladmin://$route"), context, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
