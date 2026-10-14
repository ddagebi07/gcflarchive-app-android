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
import kr.co.gcflarchive.app.databinding.FragmentSettingsBinding
import kr.co.gcflarchive.app.meal.MealKind
import kr.co.gcflarchive.app.meal.MealNotifier
import kr.co.gcflarchive.app.meal.MealRepository
import kr.co.gcflarchive.app.util.AppLock
import kr.co.gcflarchive.app.util.Biometric
import kr.co.gcflarchive.app.util.Links
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPrefs

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
        }
    }

    private fun render() {
        val b = _binding ?: return
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
