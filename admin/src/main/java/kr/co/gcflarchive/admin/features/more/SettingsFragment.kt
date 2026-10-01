package kr.co.gcflarchive.admin.features.more

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.co.gcflarchive.admin.BuildConfig
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminPrefs
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.core.Auth
import kr.co.gcflarchive.admin.core.NotifyKind
import kr.co.gcflarchive.admin.ui.LoginActivity
import kr.co.gcflarchive.admin.ui.kit.ListFragment
import kr.co.gcflarchive.admin.ui.kit.Row
import kr.co.gcflarchive.admin.ui.kit.Trailing

/** 앱 설정: 알림(종류별), 다크 모드, 화면 보호, 서버 주소, 로그아웃, 버전. */
class SettingsFragment : ListFragment() {
    private val prefs get() = AdminPrefs(requireContext())

    override suspend fun load(): List<Row> {
        val session = AdminSession.get(requireContext())
        val rows = mutableListOf(Row.header(getString(R.string.settings_notifications)))
        rows += NotifyKind.entries.map { k -> Row("notify:${k.name}", k.label, k.description, trailing = Trailing.Switch(prefs.notifyEnabled(k))) }
        rows += Row("notify_note", getString(R.string.settings_notify_how), getString(R.string.settings_notify_how_desc), icon = R.drawable.ic_info)
        rows += Row.header(getString(R.string.settings_display))
        rows += Row("theme", getString(R.string.settings_theme), themeLabel(prefs.nightMode), trailing = Trailing.Chevron)
        rows += Row("secure", getString(R.string.settings_secure), getString(R.string.settings_secure_desc), trailing = Trailing.Switch(prefs.secureScreens))
        rows += Row.header(getString(R.string.settings_account))
        rows += Row("who", session.displayName, getString(R.string.settings_permissions, if (session.grants.isUltimate) "Ultimate" else session.grants.scopes.joinToString(", ")))
        rows += Row("server", getString(R.string.login_server), prefs.serverUrl, meta = getString(R.string.settings_server_desc))
        rows += Row("logout", getString(R.string.settings_logout), icon = R.drawable.ic_lock, trailing = Trailing.Chevron)
        rows += Row.header(getString(R.string.settings_about))
        rows += Row("version", getString(R.string.app_name), getString(R.string.settings_version, BuildConfig.VERSION_NAME))
        return rows
    }

    private fun themeLabel(mode: Int) = when (mode) {
        AppCompatDelegate.MODE_NIGHT_NO -> "라이트"
        AppCompatDelegate.MODE_NIGHT_YES -> "다크"
        else -> "시스템 설정 따름"
    }

    override fun onRowSwitch(row: Row, checked: Boolean) {
        when {
            row.id.startsWith("notify:") -> prefs.setNotifyEnabled(NotifyKind.valueOf(row.id.removePrefix("notify:")), checked)
            row.id == "secure" -> prefs.secureScreens = checked
        }
    }

    override fun onRowClick(row: Row) {
        when (row.id) {
            "theme" -> {
                val modes = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_theme)
                    .setSingleChoiceItems(modes.map { themeLabel(it) }.toTypedArray(), modes.indexOf(prefs.nightMode).coerceAtLeast(0)) { d, i ->
                        prefs.nightMode = modes[i]
                        AppCompatDelegate.setDefaultNightMode(modes[i])
                        d.dismiss()
                    }
                    .show()
            }
            "logout" -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_logout)
                .setMessage(R.string.settings_logout_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.settings_logout) { _, _ ->
                    lifecycleScope.launch {
                        Auth.signOut(requireContext())
                        startActivity(Intent(requireContext(), LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    }
                }
                .show()
        }
    }
}
