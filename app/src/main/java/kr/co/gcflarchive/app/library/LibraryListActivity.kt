package kr.co.gcflarchive.app.library

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.data.LoginRequiredException
import kr.co.gcflarchive.app.databinding.ActivityLibraryBinding

/**
 * Shared frame for the native archive pages, laid out like the website's KRDS pages:
 * breadcrumb → page title → description → search/filters → "총 N건" → list.
 * The whole list comes from one API call and is filtered on the device, like the web.
 */
abstract class LibraryListActivity : AppCompatActivity() {
    protected lateinit var binding: ActivityLibraryBinding

    protected abstract val pageTitleRes: Int
    protected abstract val pageDescRes: Int
    protected abstract val searchHintRes: Int

    protected var query: String = ""
        private set

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        val title = getString(pageTitleRes)
        binding.breadcrumb.text = getString(R.string.library_breadcrumb, title)
        binding.pageTitle.text = title
        binding.pageDesc.text = getString(pageDescRes)
        binding.searchInput.hint = getString(searchHintRes)

        // Show the title in the toolbar once the page header has scrolled away.
        binding.appBar.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { _, offset ->
                val collapsed = binding.header.height > 0 && -offset >= binding.header.height - 1
                supportActionBar?.title = if (collapsed) title else ""
            },
        )

        binding.searchInput.doAfterTextChanged { text ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(200)
                query = text?.toString().orEmpty().trim()
                render()
            }
        }
        binding.swipe.setOnChildScrollUpCallback { _, _ -> binding.list.canScrollVertically(-1) }
        binding.swipe.setOnRefreshListener { reload() }

        setUpList()
        reload()
    }

    /** Configure layout manager, adapter, filter chips, action button. */
    protected abstract fun setUpList()

    /** Fetches data from the server (may throw; LoginRequiredException shows the login prompt). */
    protected abstract suspend fun fetch()

    /** Applies the current query/filters to fetched data and updates the adapter. */
    protected abstract fun render()

    protected fun reload() {
        loadJob?.cancel()
        binding.swipe.isRefreshing = true
        binding.stateGroup.isVisible = false
        loadJob = lifecycleScope.launch {
            val result = runCatching { fetch() }
            binding.swipe.isRefreshing = false
            result.onSuccess { render() }.onFailure { e ->
                when (e) {
                    is kotlinx.coroutines.CancellationException -> throw e
                    is LoginRequiredException -> showState(
                        getString(R.string.library_login_title),
                        getString(R.string.library_login_desc),
                        getString(R.string.library_login_button),
                    ) { login.launch(LoginActivity.intent(this@LibraryListActivity)) }
                    else -> showState(
                        getString(R.string.library_error_title),
                        e.message ?: getString(R.string.library_error_desc),
                        getString(R.string.retry),
                    ) { reload() }
                }
                setTotal(null)
            }
        }
    }

    /** Asks for login (e.g. favorites, 극플드라이브) and reloads afterwards. */
    protected fun requestLogin() {
        login.launch(LoginActivity.intent(this))
    }

    protected fun setTotal(count: Int?, unitRes: Int = R.string.library_total) {
        binding.total.text = count?.let { getString(unitRes, it) } ?: ""
    }

    /** Empty / error state (.board-empty-state); pass null [title] to hide it. */
    protected fun showState(title: String?, desc: String? = null, button: String? = null, onClick: (() -> Unit)? = null) {
        binding.stateGroup.isVisible = title != null
        binding.stateTitle.text = title
        binding.stateDesc.isVisible = desc != null
        binding.stateDesc.text = desc
        binding.stateButton.isVisible = button != null
        binding.stateButton.text = button
        binding.stateButton.setOnClickListener { onClick?.invoke() }
    }

    protected fun showEmptyIfNeeded(isEmpty: Boolean) {
        if (isEmpty) {
            showState(
                getString(R.string.library_empty_title),
                getString(if (query.isBlank()) R.string.library_empty_desc else R.string.library_empty_search_desc),
            )
        } else {
            showState(null)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
