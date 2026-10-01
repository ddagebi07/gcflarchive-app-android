package kr.co.gcflarchive.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import kr.co.gcflarchive.app.data.AppPrefs
import kr.co.gcflarchive.app.databinding.ActivityMainBinding
import kr.co.gcflarchive.app.ui.DriveFragment
import kr.co.gcflarchive.app.ui.HomeFragment
import kr.co.gcflarchive.app.ui.MealFragment
import kr.co.gcflarchive.app.ui.SearchFragment
import kr.co.gcflarchive.app.ui.SettingsFragment
import kr.co.gcflarchive.app.web.WebViewActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.bottomNav.setOnItemReselectedListener { item ->
            (supportFragmentManager.findFragmentByTag(tagFor(item.itemId)) as? Reselectable)?.onReselected()
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = tabFromIntent(intent) ?: R.id.tab_home
            openPathFromIntent(intent)
        }

        val prefs = AppPrefs(this)
        if (!prefs.onboardingShown) {
            prefs.onboardingShown = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && prefs.mealNotifyEnabled) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFromIntent(intent)?.let { binding.bottomNav.selectedItemId = it }
        openPathFromIntent(intent)
    }

    /** Launcher shortcuts can deep-link to a site page (e.g. /past-exams). */
    private fun openPathFromIntent(intent: Intent?) {
        val path = intent?.getStringExtra(EXTRA_PATH)?.takeIf { it.startsWith("/") } ?: return
        intent.removeExtra(EXTRA_PATH)
        startActivity(NativePages.intentFor(this, path) ?: WebViewActivity.intent(this, path))
    }

    fun selectTab(tab: String) {
        idForTab(tab)?.let { binding.bottomNav.selectedItemId = it }
    }

    /** Fragments are shown/hidden rather than replaced so the drive WebView keeps its page. */
    private fun showTab(itemId: Int) {
        val fm = supportFragmentManager
        val tag = tagFor(itemId)
        fm.commit {
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
        R.id.tab_settings -> SettingsFragment()
        else -> HomeFragment()
    }

    private fun tagFor(itemId: Int) = "tab_$itemId"

    private fun tabFromIntent(intent: Intent?): Int? = intent?.getStringExtra(EXTRA_TAB)?.let(::idForTab)

    private fun idForTab(tab: String): Int? = when (tab) {
        TAB_HOME -> R.id.tab_home
        TAB_SEARCH -> R.id.tab_search
        TAB_MEAL -> R.id.tab_meal
        TAB_DRIVE -> R.id.tab_drive
        TAB_SETTINGS -> R.id.tab_settings
        else -> null
    }

    /** Implemented by tabs that react to tapping their already-selected nav item. */
    interface Reselectable {
        fun onReselected()
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_PATH = "path"
        const val TAB_HOME = "home"
        const val TAB_SEARCH = "search"
        const val TAB_MEAL = "meal"
        const val TAB_DRIVE = "drive"
        const val TAB_SETTINGS = "settings"

        fun intent(context: Context, tab: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
