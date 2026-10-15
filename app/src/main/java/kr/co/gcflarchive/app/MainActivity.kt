package kr.co.gcflarchive.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
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

        if (savedInstanceState == null) {
            val start = tabFromIntent(intent) ?: R.id.tab_home
            binding.bottomNav.selectedItemId = start
            if (supportFragmentManager.findFragmentByTag(tagFor(start)) == null) showTab(start)
            openPathFromIntent(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFromIntent(intent)?.let { binding.bottomNav.selectedItemId = it }
        openPathFromIntent(intent)
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
