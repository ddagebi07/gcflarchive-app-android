package kr.co.gcflarchive.admin.core

import android.content.Context
import androidx.core.content.edit

/** Non-secret settings (server address, theme, notification toggles, last snapshots). */
class AdminPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(v) = prefs.edit { putString("server_url", v.trimEnd('/')) }

    /** AppCompatDelegate night mode constant. */
    var nightMode: Int
        get() = prefs.getInt("night_mode", -1)
        set(v) = prefs.edit { putInt("night_mode", v) }

    var secureScreens: Boolean
        get() = prefs.getBoolean("secure_screens", true)
        set(v) = prefs.edit { putBoolean("secure_screens", v) }

    fun notifyEnabled(kind: NotifyKind): Boolean = prefs.getBoolean("notify_${kind.name}", true)

    fun setNotifyEnabled(kind: NotifyKind, enabled: Boolean) = prefs.edit { putBoolean("notify_${kind.name}", enabled) }

    fun snapshot(key: String): String? = prefs.getString("snap_$key", null)

    fun setSnapshot(key: String, value: String?) = prefs.edit { putString("snap_$key", value) }

    fun clearSnapshots() = prefs.edit {
        prefs.all.keys.filter { it.startsWith("snap_") }.forEach { remove(it) }
    }

    companion object {
        const val DEFAULT_SERVER = "https://gcflarchive.co.kr"
    }
}

enum class NotifyKind(val label: String, val description: String) {
    AUTO_BLOCK("자동 IP 차단", "금지어 접근으로 IP 대역이 자동 차단되면 알립니다."),
    MAP_REQUEST("지도 요청·리뷰", "신규 장소·영업시간 제안·리뷰가 접수되면 알립니다."),
    MAINTENANCE("점검 모드 변경", "다른 관리자가 점검 모드를 바꾸면 알립니다."),
    ADMIN_CHANGE("관리자 계정 변경", "관리자 계정이 추가·삭제·변경되면 알립니다."),
}
