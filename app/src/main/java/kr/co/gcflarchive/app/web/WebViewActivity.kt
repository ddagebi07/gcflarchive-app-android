package kr.co.gcflarchive.app.web

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

/** Full-screen page of the website (login pages are handled natively by [GcflWebView]). */
class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebviewBinding

    private val web = GcflWebView(this, object : GcflWebView.Listener {
        override fun onLoginCancelled() {
            // Nothing on screen yet (the page itself was the login) → leave.
            if (binding.webView.url == null) finish()
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
            web.load(intent.getStringExtra(EXTRA_URL) ?: Config.BASE_URL)
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

        fun intent(context: Context, path: String, title: String? = null): Intent =
            Intent(context, WebViewActivity::class.java)
                .putExtra(EXTRA_URL, Config.url(path))
                .putExtra(EXTRA_TITLE, title)

    }
}
