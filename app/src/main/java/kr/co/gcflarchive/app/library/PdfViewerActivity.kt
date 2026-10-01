package kr.co.gcflarchive.app.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.data.LoginRequiredException
import kr.co.gcflarchive.app.data.SiteApi
import kr.co.gcflarchive.app.databinding.ActivityPdfViewerBinding
import kr.co.gcflarchive.app.databinding.ItemPdfPageBinding
import kr.co.gcflarchive.app.web.GcflWebView
import kr.co.gcflarchive.app.web.WebViewActivity
import org.json.JSONObject

/**
 * Native counterpart of the site's pdf-viewer page: renders the server's watermarked
 * page images (/api/pdf-viewer/<file>/page/N.png), so protection stays on the server.
 */
class PdfViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPdfViewerBinding
    private lateinit var filename: String
    private var includeAnswers = true
    private var pages: List<Pair<Float, Float>> = emptyList()

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        filename = intent.getStringExtra(EXTRA_FILE).orEmpty()
        includeAnswers = intent.getBooleanExtra(EXTRA_ANSWERS, true)
        supportActionBar?.title = intent.getStringExtra(EXTRA_TITLE) ?: cleanTitle(filename)

        val layout = LinearLayoutManager(this)
        binding.pages.layoutManager = layout
        binding.pages.adapter = PageAdapter()
        binding.pages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = updateIndicator(layout)
        })
        binding.btnRetry.setOnClickListener { load() }
        load()
    }

    private fun base() = "/api/pdf-viewer/${Uri.encode(filename)}"

    private fun answersQuery() = if (includeAnswers) "" else "?answers=0"

    private fun load() {
        binding.progress.isVisible = true
        binding.errorGroup.isVisible = false
        lifecycleScope.launch {
            val result = runCatching { JSONObject(SiteApi.get(base() + "/info" + answersQuery())) }
            binding.progress.isVisible = false
            result.onSuccess { json ->
                json.optString("title").takeIf { it.isNotBlank() }?.let { supportActionBar?.title = cleanTitle(it) }
                val arr = json.optJSONArray("pages")
                pages = (0 until (arr?.length() ?: 0)).map { i ->
                    val p = arr!!.getJSONArray(i)
                    p.optDouble(0).toFloat() to p.optDouble(1).toFloat()
                }
                @Suppress("NotifyDataSetChanged")
                binding.pages.adapter?.notifyDataSetChanged()
                updateIndicator(binding.pages.layoutManager as LinearLayoutManager)
            }.onFailure { e ->
                binding.errorGroup.isVisible = true
                val needLogin = e is LoginRequiredException
                binding.errorText.text = getString(if (needLogin) R.string.library_login_desc else R.string.viewer_error)
                binding.btnRetry.text = getString(if (needLogin) R.string.library_login_button else R.string.retry)
                binding.btnRetry.setOnClickListener {
                    if (needLogin) login.launch(WebViewActivity.loginIntent(this@PdfViewerActivity, "/past-exams")) else load()
                }
            }
        }
    }

    private fun updateIndicator(layout: LinearLayoutManager) {
        if (pages.isEmpty()) {
            binding.pageIndicator.isVisible = false
            return
        }
        val pos = layout.findFirstVisibleItemPosition().coerceAtLeast(0) + 1
        binding.pageIndicator.isVisible = true
        binding.pageIndicator.text = getString(R.string.viewer_page, pos, pages.size)
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun getItemCount() = pages.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            PageHolder(ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: PageHolder, position: Int) = holder.bind(position)
        override fun onViewRecycled(holder: PageHolder) = holder.unbind()
    }

    private inner class PageHolder(val b: ItemPdfPageBinding) : RecyclerView.ViewHolder(b.root) {
        private var job: Job? = null

        fun bind(index: Int) {
            val (w, h) = pages[index]
            val width = binding.pages.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            b.image.layoutParams = b.image.layoutParams.apply { height = (width * (h / w)).toInt() }
            b.image.setImageBitmap(null)
            b.loading.isVisible = true
            val url = base() + "/page/${index + 1}.png" + answersQuery()
            job?.cancel()
            job = lifecycleScope.launch {
                // Pages are decoded at up to 2x screen width so zooming stays sharp.
                val bitmap: Bitmap? = ImageLoader.load(url, width * 2, lowMemory = true)
                b.loading.isVisible = false
                if (bitmap != null) b.image.setImageBitmap(bitmap)
            }
        }

        fun unbind() {
            job?.cancel()
            b.image.setImageBitmap(null)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.pdf_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_zoom_reset -> { binding.zoom.reset(); true }
        R.id.action_download -> {
            GcflWebView.download(
                this,
                LibraryRepository.downloadUrl(filename, includeAnswers),
                fileName = supportActionBar?.title?.toString()?.let { "$it.pdf" } ?: filename,
                mimeType = "application/pdf",
            )
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        private const val EXTRA_FILE = "file"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ANSWERS = "answers"

        fun intent(context: Context, filename: String, title: String?, includeAnswers: Boolean = true): Intent =
            Intent(context, PdfViewerActivity::class.java)
                .putExtra(EXTRA_FILE, filename)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ANSWERS, includeAnswers)
    }
}
