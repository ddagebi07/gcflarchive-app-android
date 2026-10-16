package kr.co.gcflarchive.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.gcflarchive.app.BuildConfig
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.data.AppPrefs
import kr.co.gcflarchive.app.data.Http
import kr.co.gcflarchive.app.data.LoginState
import kr.co.gcflarchive.app.data.SiteSession
import kr.co.gcflarchive.app.databinding.DialogInputBinding
import kr.co.gcflarchive.app.databinding.FragmentSettingsBinding
import kr.co.gcflarchive.app.map.MapLogic
import kr.co.gcflarchive.app.map.MapRepository
import kr.co.gcflarchive.app.map.NicknameStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.text.InputType
import kr.co.gcflarchive.app.meal.MealKind
import kr.co.gcflarchive.app.meal.MealNotifier
import kr.co.gcflarchive.app.notice.NoticeNotifier
import kr.co.gcflarchive.app.meal.MealRepository
import kr.co.gcflarchive.app.util.AppLock
import kr.co.gcflarchive.app.util.Biometric
import kr.co.gcflarchive.app.util.Links
import kr.co.gcflarchive.core.i18n.I18n
import kr.co.gcflarchive.app.GcflApp
import kr.co.gcflarchive.student.ui.ThemeMode
import kr.co.gcflarchive.core.prefs.AppPrefs as CorePrefs
import kr.co.gcflarchive.student.school.SchoolActivity
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPrefs
    private var nickname: NicknameStatus? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(requireContext(), R.string.notify_permission_denied, Toast.LENGTH_LONG).show()
        }
        render()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = AppPrefs(requireContext())

        binding.switchMeal.setOnCheckedChangeListener { _, checked ->
            if (checked == prefs.mealNotifyEnabled) return@setOnCheckedChangeListener
            prefs.mealNotifyEnabled = checked
            MealNotifier.schedule(requireContext(), replace = true)
            if (checked) ensurePermission()
            render()
        }
        binding.switchNotice.isChecked = prefs.noticeNotifyEnabled
        binding.switchNotice.setOnCheckedChangeListener { _, checked ->
            if (checked == prefs.noticeNotifyEnabled) return@setOnCheckedChangeListener
            prefs.noticeNotifyEnabled = checked
            NoticeNotifier.schedule(requireContext(), replace = true)
            if (checked) ensurePermission()
        }
        // Shared with 학교생활 (student module): same settings store as its timetable.
        val corePrefs = kr.co.gcflarchive.core.prefs.AppPrefs()
        binding.switchTimetableFull.isChecked = kr.co.gcflarchive.student.school.TimetableNames.full(corePrefs)
        binding.switchTimetableFull.setOnCheckedChangeListener { _, checked ->
            kr.co.gcflarchive.student.school.TimetableNames.set(corePrefs, checked)
        }
        binding.rowTime.setOnClickListener { pickTime() }
        binding.rowKind.setOnClickListener { pickKind() }
        binding.rowTest.setOnClickListener { sendTest() }
        binding.rowSystem.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName),
            )
        }
        binding.switchAppLock.setOnCheckedChangeListener { button, checked ->
            if (checked == prefs.appLockEnabled) return@setOnCheckedChangeListener
            button.isChecked = !checked // Only flips once the change is confirmed.
            changeAppLock(checked)
        }
        binding.rowNickname.setOnClickListener { editNickname() }
        binding.rowLanguage.setOnClickListener { pickLanguage() }
        binding.rowTheme.setOnClickListener { pickTheme() }
        binding.rowLogin.setOnClickListener { startActivity(LoginActivity.intent(requireContext())) }
        binding.rowLogout.setOnClickListener { logout() }
        binding.rowWebsite.setOnClickListener { Links.openExternal(requireContext(), android.net.Uri.parse(Config.BASE_URL)) }
        binding.version.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
        renderAccount()
    }

    /** Shows 로그인 or 로그아웃 depending on the site session (both while offline). */
    private fun renderAccount() {
        viewLifecycleOwner.lifecycleScope.launch {
            val state = SiteSession.state()
            val b = _binding ?: return@launch
            b.rowLogin.isVisible = state !is LoginState.LoggedIn
            b.rowLogout.isVisible = state != LoginState.LoggedOut
            b.rowNickname.isVisible = state is LoginState.LoggedIn
            if (state is LoginState.LoggedIn) {
                nickname = runCatching { MapRepository.nickname() }.getOrNull()
                _binding?.nicknameValue?.text = nickname?.nickname?.ifBlank { null } ?: getString(R.string.settings_nickname_none)
            }
            renderLanguage(state)
        }
    }

    /** 화면 언어 (학교생활 선생님 화면과 알림): only for teachers, and only when the server has more than 한국어. */
    private fun renderLanguage(state: LoginState? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (state) {
                is LoginState.LoggedIn -> I18n.sync(SchoolActivity.services(requireContext()).site)
                LoginState.LoggedOut -> I18n.reset()
                else -> {}  // offline, or just redraw
            }
            val b = _binding ?: return@launch
            b.rowLanguage.isVisible = true  // 누구나: 이 기기의 화면 언어 (선생님은 알림도 그 언어로)
            b.languageLabel.text = I18n.tr("화면 언어")
            b.languageValue.text = I18n.languages.ifEmpty { I18n.fallbackLanguages }.firstOrNull { it.code == I18n.lang }?.name ?: I18n.lang
        }
    }

    /** 화면 모드: 시스템 설정 따르기(기본) / 라이트 / 다크. Applies at once to every screen. */
    private fun pickTheme() {
        val modes = CorePrefs.THEME_MODES
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(modes.map(CorePrefs::themeLabel).toTypedArray(), modes.indexOf(ThemeMode.mode)) { dialog, which ->
                dialog.dismiss()
                ThemeMode.set(modes[which])          // shared screens (학교생활, 오늘 시간표)
                GcflApp.applyThemeMode(modes[which])  // this app's screens; recreates them in the new mode
                render()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun pickLanguage() {
        val langs = I18n.languages.ifEmpty { I18n.fallbackLanguages }
        val current = langs.indexOfFirst { it.code == I18n.lang }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(I18n.tr("화면 언어"))
            .setSingleChoiceItems(langs.map { it.name }.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    val site = SchoolActivity.services(requireContext()).site
                    runCatching { I18n.choose(site, langs[which].code) }
                        .onFailure { e -> context?.let { Toast.makeText(it, e.message, Toast.LENGTH_SHORT).show() } }
                    // Every screen (and the tabs behind) is redrawn in the new language: restart from 홈.
                    val ctx = context ?: return@launch
                    startActivity(android.content.Intent(ctx, kr.co.gcflarchive.app.MainActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
            }
            .setNegativeButton(I18n.tr("닫기"), null)
            .show()
    }

    private fun render() {
        val b = _binding ?: return
        b.themeValue.text = CorePrefs.themeLabel(ThemeMode.mode)
        b.switchMeal.isChecked = prefs.mealNotifyEnabled
        b.switchAppLock.isChecked = prefs.appLockEnabled
        val minute = prefs.mealNotifyMinuteOfDay
        b.timeValue.text = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
        b.kindValue.text = MealKind.label(prefs.mealNotifyKinds)
        b.rowTime.isEnabled = prefs.mealNotifyEnabled
        b.rowKind.isEnabled = prefs.mealNotifyEnabled
        b.permissionWarning.visibility =
            if (prefs.mealNotifyEnabled && !MealNotifier.canPost(requireContext())) View.VISIBLE else View.GONE
    }

    /** Turning it on or off both need the device lock, so nobody else can switch it off. */
    private fun changeAppLock(enable: Boolean) {
        val activity = activity ?: return
        if (!Biometric.isAvailable(activity)) {
            Toast.makeText(activity, R.string.settings_app_lock_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val title = getString(if (enable) R.string.settings_app_lock_on_title else R.string.settings_app_lock_off_title)
        Biometric.authenticate(activity, title, null) {
            prefs.appLockEnabled = enable
            AppLock.markUnlocked()
            Toast.makeText(activity, if (enable) R.string.settings_app_lock_on else R.string.settings_app_lock_off, Toast.LENGTH_SHORT).show()
            render()
        }
    }

    /** 지도 후기와 커뮤니티에 함께 쓰는 닉네임. 바꾸면 예전 후기·글·댓글에도 새 이름이 보여요. */
    private fun editNickname() {
        val ctx = context ?: return
        val status = nickname
        val field = DialogInputBinding.inflate(layoutInflater)
        field.inputLayout.hint = getString(R.string.map_nickname_hint)
        field.input.inputType = InputType.TYPE_CLASS_TEXT
        field.input.setText(status?.nickname.orEmpty())
        field.input.setSelection(field.input.text?.length ?: 0)
        val locked = status != null && status.nickname.isNotBlank() && !status.canChange
        val message = when {
            locked -> getString(R.string.settings_nickname_wait, status!!.changeDays, nextChangeLabel(status.nextChangeAt))
            else -> getString(R.string.settings_nickname_desc, status?.changeDays ?: 7)
        }
        field.input.isEnabled = !locked
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(if (status?.nickname.isNullOrBlank()) R.string.community_nickname_title else R.string.settings_nickname_change)
            .setMessage(message)
            .setView(field.root)
            .setNegativeButton(R.string.close, null)
            .apply { if (!locked) setPositiveButton(R.string.community_save, null) }
            .show()
        if (locked) return
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = field.input.text?.toString().orEmpty().trim()
            MapLogic.nicknameError(name)?.let {
                field.inputLayout.error = it
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { MapRepository.setNickname(name) }
                    .onSuccess {
                        dialog.dismiss()
                        Toast.makeText(ctx, getString(R.string.settings_nickname_saved, name), Toast.LENGTH_SHORT).show()
                        renderAccount()
                    }
                    .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e else field.inputLayout.error = e.message }
            }
        }
    }

    /** "10월 21일 17:05" in Korean time, or the raw value. */
    private fun nextChangeLabel(iso: String): String = runCatching {
        java.time.Instant.parse(iso).atZone(java.time.ZoneId.of("Asia/Seoul"))
            .format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
    }.getOrDefault(iso)

    private fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !MealNotifier.canPost(requireContext())) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun pickTime() {
        val minute = prefs.mealNotifyMinuteOfDay
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(minute / 60)
            .setMinute(minute % 60)
            .setTitleText(R.string.notify_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            prefs.mealNotifyMinuteOfDay = picker.hour * 60 + picker.minute
            // Let the new time fire today even if today's notification already went out earlier.
            prefs.lastNotifiedDate = null
            MealNotifier.schedule(requireContext(), replace = true)
            render()
        }
        picker.show(childFragmentManager, "time")
    }

    /** 중복 선택: 점심·저녁 any of none / one / both; saved on each tap. */
    private fun pickKind() {
        val all = MealKind.entries
        val checked = all.map { it in prefs.mealNotifyKinds }.toBooleanArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.notify_kind)
            .setMultiChoiceItems(all.map { it.label }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
                prefs.mealNotifyKinds = all.filterIndexed { i, _ -> checked[i] }.toSet()
                render()
            }
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun sendTest() {
        ensurePermission()
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            val meals = runCatching { MealRepository.fetch(MealRepository.today()) }.getOrElse {
                Toast.makeText(ctx, R.string.meal_load_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (!MealNotifier.post(ctx, meals, prefs.mealNotifyKinds)) {
                val msg = when {
                    !MealNotifier.canPost(ctx) -> R.string.notify_permission_denied
                    prefs.mealNotifyKinds.isEmpty() -> R.string.notify_kind_none
                    else -> R.string.meal_none_today
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logout() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cookies = CookieManager.getInstance()
            withContext(Dispatchers.IO) {
                runCatching {
                    val cookie = cookies.getCookie(Config.BASE_URL) ?: return@runCatching
                    Http.client.newCall(
                        Request.Builder()
                            .url(Config.url("/api/verify-logout"))
                            .header("Cookie", cookie)
                            .post("{}".toRequestBody())
                            .build(),
                    ).execute().close()
                }
            }
            cookies.removeAllCookies(null)
            cookies.flush()
            context?.let { Toast.makeText(it, R.string.logged_out, Toast.LENGTH_SHORT).show() }
            renderAccount()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
