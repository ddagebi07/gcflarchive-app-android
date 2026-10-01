package kr.co.gcflarchive.admin.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.co.gcflarchive.admin.R
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.core.Auth
import kr.co.gcflarchive.admin.databinding.ActivityMainBinding
import kr.co.gcflarchive.admin.features.dashboard.DashboardFragment
import kr.co.gcflarchive.admin.ui.search.SearchActivity

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private var pendingSecurityRoute: Route? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        val session = AdminSession.get(this)
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.subtitle = session.displayName

        applyTabVisibility()
        binding.bottomNav.setOnItemSelectedListener { show(it.itemId); true }
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.tab_home
            handleDeepLink(intent)
        }

        // Scopes can change on the server; refresh them for hakbun sessions.
        if (session.mode == AdminSession.Mode.HAKBUN) {
            lifecycleScope.launch {
                runCatching { Auth.fetchScopes(this@MainActivity) }.getOrNull()?.let {
                    session.updateScopes(it)
                    applyTabVisibility()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /** 권한 기반 메뉴 노출: tabs with nothing permitted are hidden. */
    private fun applyTabVisibility() {
        val g = AdminSession.get(this).grants
        val menu = binding.bottomNav.menu
        menu.findItem(R.id.tab_security).isVisible = Route.forTab(Tab.SECURITY, g).isNotEmpty()
        menu.findItem(R.id.tab_content).isVisible = Route.forTab(Tab.CONTENT, g).isNotEmpty()
        menu.findItem(R.id.tab_logs).isVisible = Route.forTab(Tab.LOGS, g).isNotEmpty()
    }

    private fun show(itemId: Int) {
        val tag = "tab_$itemId"
        val fm = supportFragmentManager
        fm.commit {
            setReorderingAllowed(true)
            fm.fragments.filter { it.tag != tag && !it.isHidden }.forEach { hide(it) }
            val existing = fm.findFragmentByTag(tag)
            if (existing == null) add(binding.container.id, create(itemId), tag) else show(existing)
        }
        invalidateOptionsMenu()
    }

    private fun create(itemId: Int): Fragment = when (itemId) {
        R.id.tab_security -> TabsFragment().apply {
            arguments = Bundle().apply { pendingSecurityRoute?.let { putString(SectionActivity.EXTRA_ROUTE, it.key) } }
            pendingSecurityRoute = null
        }
        R.id.tab_content -> HubFragment.of(Tab.CONTENT)
        R.id.tab_logs -> HubFragment.of(Tab.LOGS)
        R.id.tab_more -> HubFragment.of(Tab.MORE)
        else -> DashboardFragment()
    }

    /** 딥링크: notifications, shortcuts and the widget open the matching screen. */
    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        val key = intent.getStringExtra(EXTRA_ROUTE) ?: intent.data?.takeIf { it.scheme == "gcfladmin" }?.host ?: return
        intent.removeExtra(EXTRA_ROUTE)
        intent.data = null
        if (key == "home" || key == "maintenance") {
            binding.bottomNav.selectedItemId = R.id.tab_home
            return
        }
        val route = Route.of(key) ?: return
        if (!route.visible(AdminSession.get(this).grants)) return
        if (route.tab == Tab.SECURITY) {
            val existing = supportFragmentManager.findFragmentByTag("tab_${R.id.tab_security}") as? TabsFragment
            if (existing?.view != null) existing.select(route) else pendingSecurityRoute = route
            binding.bottomNav.selectedItemId = R.id.tab_security
        } else {
            Navigator.open(this, route)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_search) {
            startActivity(Intent(this, SearchActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun openTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    companion object {
        const val EXTRA_ROUTE = "route"

        fun intent(context: Context, routeKey: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ROUTE, routeKey)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
