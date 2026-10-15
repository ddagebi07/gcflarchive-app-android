package kr.co.gcflarchive.app.update

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.BuildConfig
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.data.AppPrefs
import kr.co.gcflarchive.app.data.SiteApi
import kr.co.gcflarchive.app.util.Links

/**
 * 앱 업데이트 안내: 홈페이지(베타 페이지)에 더 새 버전이 올라와 있으면 앱을 켤 때 한 번 알려줘요.
 * '나중에'를 고르면 그 버전은 다시 묻지 않아요. 조용히 실패해요 (오프라인 등).
 */
object AppUpdate {
    fun checkOnLaunch(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val latest = runCatching { UpdateLogic.parse(SiteApi.get("/api/beta/latest?app=student")) }.getOrNull()
            val prefs = AppPrefs(activity)
            if (!UpdateLogic.shouldPrompt(latest, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, prefs.skippedUpdateVersion)) return@launch
            if (activity.isFinishing || activity.isDestroyed || latest == null) return@launch
            val message = buildString {
                append(activity.getString(R.string.update_message, latest.versionName, BuildConfig.VERSION_NAME))
                if (latest.notes.isNotBlank()) append("\n\n").append(latest.notes.take(400))
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_title)
                .setMessage(message)
                .setPositiveButton(R.string.update_go) { _, _ ->
                    Links.openExternal(activity, android.net.Uri.parse(Config.url(latest.page)))
                }
                .setNegativeButton(R.string.update_later) { _, _ -> prefs.skippedUpdateVersion = latest.versionName }
                .show()
        }
    }
}
