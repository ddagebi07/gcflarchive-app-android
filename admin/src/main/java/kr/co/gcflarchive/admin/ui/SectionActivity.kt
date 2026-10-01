package kr.co.gcflarchive.admin.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.commit
import kr.co.gcflarchive.admin.core.AdminSession
import kr.co.gcflarchive.admin.databinding.ActivitySectionBinding

/** Hosts one [Route] screen full-screen (from hubs, search, deep links). */
class SectionActivity : BaseActivity() {
    private lateinit var binding: ActivitySectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val route = Route.of(intent.getStringExtra(EXTRA_ROUTE))
        val factory = route?.create
        // Never open a screen the operator has no permission for (e.g. stale deep link).
        if (route == null || factory == null || !route.visible(AdminSession.get(this).grants)) {
            finish()
            return
        }
        supportActionBar?.title = route.title
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(binding.container.id, factory().apply {
                    arguments = (arguments ?: Bundle()).apply { putAll(intent.extras ?: Bundle()) }
                })
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_ROUTE = "route"

        fun intent(context: Context, route: Route): Intent =
            Intent(context, SectionActivity::class.java).putExtra(EXTRA_ROUTE, route.key)
    }
}
