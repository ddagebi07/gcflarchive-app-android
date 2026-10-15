package kr.co.gcflarchive.admin.update

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.co.gcflarchive.admin.BuildConfig
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminApi
import kr.co.gcflarchive.admin.core.AdminPrefs

/** 관리자 앱 업데이트 안내 (학생 앱과 같아요): 새 버전이 올라와 있으면 켤 때 한 번, '나중에'면 그 버전은 다시 묻지 않아요. */
object AppUpdate {
    private const val KEY_SKIPPED = "skipped_update_version"

    fun checkOnLaunch(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val api = AdminApi.get(activity)
            val latest = runCatching { UpdateLogic.parse(api.get("/api/beta/latest?app=admin", cache = false).body) }.getOrNull()
            val prefs = activity.getSharedPreferences("admin_prefs", android.content.Context.MODE_PRIVATE)
            val skipped = prefs.getString(KEY_SKIPPED, null)
            if (!UpdateLogic.shouldPrompt(latest, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, skipped)) return@launch
            if (activity.isFinishing || activity.isDestroyed || latest == null) return@launch
            val message = buildString {
                append(activity.getString(R.string.update_message, latest.versionName, BuildConfig.VERSION_NAME))
                if (latest.notes.isNotBlank()) append("\n\n").append(latest.notes.take(400))
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_title)
                .setMessage(message)
                .setPositiveButton(R.string.update_go) { _, _ ->
                    CustomTabsIntent.Builder().setShowTitle(true).build()
                        .launchUrl(activity, Uri.parse(AdminPrefs(activity).serverUrl + latest.page))
                }
                .setNegativeButton(R.string.update_later) { _, _ -> prefs.edit().putString(KEY_SKIPPED, latest.versionName).apply() }
                .show()
        }
    }
}
