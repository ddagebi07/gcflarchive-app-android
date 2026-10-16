package kr.co.gcflarchive.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.data.LoginState
import kr.co.gcflarchive.app.data.SiteSession
import kr.co.gcflarchive.app.data.AppPrefs
import kr.co.gcflarchive.app.databinding.ActivityMainBinding
import kr.co.gcflarchive.app.ui.DriveFragment
import kr.co.gcflarchive.app.ui.HomeFragment
import kr.co.gcflarchive.app.ui.MealFragment
import kr.co.gcflarchive.app.ui.SearchFragment
import kr.co.gcflarchive.app.ui.MoreFragment
import kr.co.gcflarchive.app.update.AppUpdate
import kr.co.gcflarchive.app.util.Links

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * 로그인 관문: the app can't be used logged out. The full-screen login opens first;
     * backing out of it closes the app.
     */
    private var gateOpen = false
    private val loginGate = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        gateOpen = false
        if (r.resultCode == Activity.RESULT_OK) {
            AppPrefs(this).signedIn = true
            // Tabs that loaded while logged out (홈 인사, 드라이브) refresh on resume.
        } else {
            finish()
        }
    }

    /** 첫 실행 언어 질문이 떠 있는 동안은 로그인 화면을 띄우지 않아요 (질문이 가려지지 않게). */
    private var askingLanguage = false

    /**
     * 처음 실행할 때 한 번: 기기 언어가 한국어면 한국어, 아니면 한국어 / English를 물어봐요 (두 언어로 안내,
     * 설정 > 화면 언어에서 바꿀 수 있다고 알려요). Returns true while the question is showing.
     */
    private fun askLanguageOnFirstRun(): Boolean {
        if (!kr.co.gcflarchive.core.i18n.I18n.needsFirstChoice(java.util.Locale.getDefault().language)) return false
        askingLanguage = true
        val site = kr.co.gcflarchive.student.school.SchoolActivity.services(this).site
        val pick = { code: String ->
            lifecycleScope.launch {
                runCatching { kr.co.gcflarchive.core.i18n.I18n.choose(site, code) }
                askingLanguage = false
                // Re-create so every label is drawn in the chosen language, then the login comes up if needed.
                recreate()
            }
            Unit
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("언어 / Language")
            .setMessage("앱에서 쓸 언어를 골라주세요.\nChoose the language for the app.\n\n설정 > 화면 언어에서 언제든 바꿀 수 있어요.\nYou can change it anytime in Settings > Display language.")
            .setCancelable(false)
            .setPositiveButton("English") { _, _ -> pick("en") }
            .setNegativeButton("한국어") { _, _ -> pick(kr.co.gcflarchive.core.i18n.I18n.DEFAULT) }
            .show()
        return true
    }

    private fun openLoginGate() {
        if (gateOpen || isFinishing || askingLanguage) return
        gateOpen = true
        loginGate.launch(LoginActivity.intent(this, gate = true))
    }

    /** Re-checks on every return: the session can expire, or the user logged out in 설정. */
    private fun checkLogin() {
        if (gateOpen || askingLanguage) return
        lifecycleScope.launch {
            when (SiteSession.state()) {
                is LoginState.LoggedIn -> AppPrefs(this@MainActivity).signedIn = true
                LoginState.LoggedOut -> {
                    AppPrefs(this@MainActivity).signedIn = false
                    openLoginGate()
                }
                // Offline: someone who logged in before keeps using the app (meals, saved data).
                LoginState.Unknown -> if (!mayBeSignedIn()) openLoginGate()
            }
        }
    }

    /** Logged in before (or a session cookie from before this check existed, e.g. after an update). */
    private fun mayBeSignedIn(): Boolean = AppPrefs(this).signedIn ||
        runCatching { CookieManager.getInstance().getCookie(Config.BASE_URL)?.contains("gcfl_session=") == true }.getOrDefault(false)

    override fun onResume() {
        super.onResume()
        checkLogin()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.bottomNav.setOnItemReselectedListener { item ->
            val existing = supportFragmentManager.findFragmentByTag(tagFor(item.itemId))
            // 홈 is selected from the start, so "selecting" it at launch arrives here as a
            // reselect: create the tab instead of leaving the screen empty.
            if (existing == null) showTab(item.itemId) else (existing as? Reselectable)?.onReselected()
        }

        gateOpen = savedInstanceState?.getBoolean(STATE_GATE) == true
        askLanguageOnFirstRun()
        // 화면 언어: the list and the chosen language's strings (teachers: the same language for 알림).
        lifecycleScope.launch { runCatching { kr.co.gcflarchive.core.i18n.I18n.sync(kr.co.gcflarchive.student.school.SchoolActivity.services(this@MainActivity).site) } }
        // Never logged in on this device: the login comes first, before the tabs load anything.
        if (!mayBeSignedIn()) openLoginGate()

        if (savedInstanceState == null) {
            val start = tabFromIntent(intent) ?: R.id.tab_home
            binding.bottomNav.selectedItemId = start
            if (supportFragmentManager.findFragmentByTag(tagFor(start)) == null) showTab(start)
            openPathFromIntent(intent)
            openPushTarget(intent)
            AppUpdate.checkOnLaunch(this)
        }

        val prefs = AppPrefs(this)
        if (!prefs.onboardingShown) {
            prefs.onboardingShown = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && prefs.mealNotifyEnabled) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_GATE, gateOpen)
    }

    /** A 학교생활 push tapped in the tray (app in the background) arrives here with its data as extras. */
    private fun openPushTarget(intent: Intent?) {
        val target = intent?.getStringExtra("target") ?: return
        if (intent.getStringExtra("google.message_id") == null && intent.getStringExtra("kind") == null) return
        intent.removeExtra("target")
        startActivity(kr.co.gcflarchive.student.school.SchoolActivity.intent(this, target = target))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFromIntent(intent)?.let { binding.bottomNav.selectedItemId = it }
        openPathFromIntent(intent)
        openPushTarget(intent)
    }

    /** Launcher shortcuts and shared links can deep-link to a site page (e.g. /past-exams). */
    private fun openPathFromIntent(intent: Intent?) {
        // A launcher shortcut's path, or a shared https://gcflarchive.co.kr/… link.
        val link = intent?.data?.takeIf { intent?.action == Intent.ACTION_VIEW && Config.isOwnHost(it) }
        val path = intent?.getStringExtra(EXTRA_PATH)?.takeIf { it.startsWith("/") }
            ?: link?.let { (it.encodedPath ?: "/") + (it.encodedQuery?.let { q -> "?$q" } ?: "") }
            ?: return
        intent?.removeExtra(EXTRA_PATH)
        intent?.data = null
        val target = NativePages.intentFor(this, path)
        when {
            target == null -> Links.openExternal(this, android.net.Uri.parse(Config.url(path)))
            // Tabs of this activity: switch instead of stacking a second copy.
            target.component?.className == MainActivity::class.java.name -> target.getStringExtra(EXTRA_TAB)?.let(::selectTab)
            else -> startActivity(target)
        }
    }

    fun selectTab(tab: String) {
        idForTab(tab)?.let { binding.bottomNav.selectedItemId = it }
    }

    /** Fragments are shown/hidden rather than replaced so each tab keeps its state. */
    private fun showTab(itemId: Int) {
        val fm = supportFragmentManager
        val tag = tagFor(itemId)
        // commitNow: at launch 홈 is "selected" twice (reselect listener + the check in onCreate);
        // an async commit isn't visible to findFragmentByTag yet, which stacked two 홈 screens.
        fm.commitNow {
            setReorderingAllowed(true)
            fm.fragments.filter { it.tag != tag && !it.isHidden }.forEach { hide(it) }
            val existing = fm.findFragmentByTag(tag)
            if (existing == null) add(R.id.container, newFragment(itemId), tag) else show(existing)
        }
    }

    private fun newFragment(itemId: Int): Fragment = when (itemId) {
        R.id.tab_search -> SearchFragment()
        R.id.tab_meal -> MealFragment()
        R.id.tab_drive -> DriveFragment()
        R.id.tab_more -> MoreFragment()
        else -> HomeFragment()
    }

    private fun tagFor(itemId: Int) = "tab_$itemId"

    private fun tabFromIntent(intent: Intent?): Int? = intent?.getStringExtra(EXTRA_TAB)?.let(::idForTab)

    private fun idForTab(tab: String): Int? = when (tab) {
        TAB_HOME -> R.id.tab_home
        TAB_SEARCH -> R.id.tab_search
        TAB_MEAL -> R.id.tab_meal
        TAB_DRIVE -> R.id.tab_drive
        TAB_MORE, TAB_SETTINGS -> R.id.tab_more
        else -> null
    }

    /** Implemented by tabs that react to tapping their already-selected nav item. */
    interface Reselectable {
        fun onReselected()
    }

    companion object {
        private const val STATE_GATE = "login_gate_open"
        const val EXTRA_TAB = "tab"
        const val EXTRA_PATH = "path"
        const val TAB_HOME = "home"
        const val TAB_SEARCH = "search"
        const val TAB_MEAL = "meal"
        const val TAB_DRIVE = "drive"
        const val TAB_MORE = "more"
        /** Older shortcuts / links: 설정 now lives under 더보기. */
        const val TAB_SETTINGS = "settings"

        fun intent(context: Context, tab: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
