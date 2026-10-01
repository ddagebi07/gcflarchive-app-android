package kr.co.gcflarchive.app.web

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ActivityWebviewBinding

/**
 * Full-screen page of the website. With [EXTRA_FINISH_ON_PATH] set it doubles as a
 * login screen: once the site navigates to that path (i.e. login succeeded and the
 * verify page redirected to `next`), it finishes with RESULT_OK.
 */
class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebviewBinding
    private var finishOnPath: String? = null

    private val web = GcflWebView(this, object : GcflWebView.Listener {
        override fun onPageStarted(url: String) {
            val target = finishOnPath ?: return
            val path = Uri.parse(url).path?.trimEnd('/')
            if (Config.isOwnHost(Uri.parse(url)) && path == target.trimEnd('/')) {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }

        override fun onProgress(progress: Int) {
            binding.progress.progress = progress
            binding.progress.visibility = if (progress < 100) View.VISIBLE else View.GONE
        }

        override fun onPageFinished(url: String) {
            binding.swipe.isRefreshing = false
        }

        override fun onTitle(title: String) {
            if (intent.getStringExtra(EXTRA_TITLE) == null) {
                supportActionBar?.title = title.substringBefore(" - ").trim()
            }
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        finishOnPath = intent.getStringExtra(EXTRA_FINISH_ON_PATH)

        web.attach(binding.webView)
        binding.swipe.setOnRefreshListener { binding.webView.reload() }
        // Only allow pull-to-refresh when the page is scrolled to the top.
        binding.swipe.setOnChildScrollUpCallback { _, _ -> binding.webView.scrollY > 0 }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
            }
        })

        if (savedInstanceState == null || binding.webView.restoreState(savedInstanceState) == null) {
            binding.webView.loadUrl(intent.getStringExtra(EXTRA_URL) ?: Config.BASE_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.webview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_refresh -> { binding.webView.reload(); true }
        R.id.action_open_browser -> {
            binding.webView.url?.let { GcflWebView.openExternal(this, Uri.parse(it)) }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_FINISH_ON_PATH = "finish_on_path"

        fun intent(context: Context, path: String, title: String? = null): Intent =
            Intent(context, WebViewActivity::class.java)
                .putExtra(EXTRA_URL, Config.url(path))
                .putExtra(EXTRA_TITLE, title)

        /** Opens the site's login page; the activity result is OK once login lands on [next]. */
        fun loginIntent(context: Context, next: String): Intent =
            Intent(context, WebViewActivity::class.java)
                .putExtra(EXTRA_URL, Config.loginUrl(next))
                .putExtra(EXTRA_TITLE, context.getString(R.string.login_title))
                .putExtra(EXTRA_FINISH_ON_PATH, next)
    }
}
