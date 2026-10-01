package kr.co.gcflarchive.app.archive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ActivityPostDetailBinding
import kr.co.gcflarchive.app.databinding.ItemAttachmentBinding
import kr.co.gcflarchive.app.web.GcflWebView

/** One school-homepage post from the archive, with its attachments and prev/next navigation. */
class PostDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPostDetailBinding
    private lateinit var boardCode: String
    private lateinit var idx: String
    private var post: PostDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        boardCode = intent.getStringExtra(EXTRA_BOARD).orEmpty()
        idx = savedInstanceState?.getString(EXTRA_IDX) ?: intent.getStringExtra(EXTRA_IDX).orEmpty()

        // School posts are static HTML: no JavaScript, and links leave the app.
        binding.content.settings.javaScriptEnabled = false
        binding.content.setBackgroundColor(0)
        binding.content.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                GcflWebView.openExternal(this@PostDetailActivity, request.url)
                return true
            }
        }
        binding.btnRetry.setOnClickListener { load() }
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_IDX, idx)
    }

    private fun load() {
        binding.progress.isVisible = true
        binding.errorGroup.isVisible = false
        binding.body.isVisible = false
        lifecycleScope.launch {
            val result = runCatching { ArchiveRepository.detail(boardCode, idx) }
            binding.progress.isVisible = false
            result.onSuccess(::render).onFailure { binding.errorGroup.isVisible = true }
        }
    }

    private fun render(p: PostDetail) {
        post = p
        invalidateOptionsMenu()
        binding.body.isVisible = true
        binding.scroll.scrollTo(0, 0)
        supportActionBar?.title = p.boardName
        binding.board.text = p.boardName
        binding.title.text = p.title
        binding.meta.text = getString(R.string.post_meta, p.author, ArchiveRepository.displayDate(p.postedAt))

        val baseUrl = p.originalUrl ?: "https://www.gcfl.or.kr/"
        binding.content.loadDataWithBaseURL(baseUrl, wrapHtml(p.contentHtml), "text/html", "utf-8", null)

        binding.attachmentsHeader.isVisible = p.attachments.isNotEmpty()
        binding.attachments.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (a in p.attachments) {
            val row = ItemAttachmentBinding.inflate(inflater, binding.attachments, false)
            row.name.text = a.filename
            row.size.text = if (a.fileSize > 0) Formatter.formatShortFileSize(this, a.fileSize) else ""
            row.root.setOnClickListener {
                GcflWebView.download(this, a.downloadUrl, fileName = a.filename)
            }
            binding.attachments.addView(row.root)
        }

        bindNav(binding.btnPrev, p.prev)
        bindNav(binding.btnNext, p.next)
        binding.navGroup.isVisible = p.prev != null || p.next != null
    }

    private fun bindNav(button: com.google.android.material.button.MaterialButton, ref: PostRef?) {
        button.isEnabled = ref != null
        button.setOnClickListener {
            ref ?: return@setOnClickListener
            idx = ref.idx
            load()
        }
    }

    /** Wraps the crawled fragment so it fits the phone width and follows the app's light/dark theme. */
    private fun wrapHtml(fragment: String): String {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val fg = if (night) "#E6E8EE" else "#1B1F27"
        val link = if (night) "#8AB8FF" else "#0B3492"
        return """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body{margin:0;color:$fg;font-size:16px;line-height:1.7;word-break:keep-all;overflow-wrap:anywhere}
              *{max-width:100%!important;box-sizing:border-box}
              img,video,iframe{height:auto!important}
              table{border-collapse:collapse;display:block;overflow-x:auto}
              td,th{border:1px solid #8884;padding:4px}
              a{color:$link}
              p{margin:0 0 .6em}
              ${if (night) "*{background-color:transparent!important;color:$fg!important}" else ""}
            </style></head><body>$fragment</body></html>
        """.trimIndent()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.post_detail, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_original)?.isVisible = post?.originalUrl != null
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_original -> {
            post?.originalUrl?.let { GcflWebView.openExternal(this, Uri.parse(it)) }
            true
        }
        R.id.action_share -> {
            post?.let { p ->
                val text = listOfNotNull(p.title, p.originalUrl).joinToString("\n")
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        binding.content.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_BOARD = "board"
        private const val EXTRA_IDX = "idx"

        fun intent(context: Context, boardCode: String, idx: String): Intent =
            Intent(context, PostDetailActivity::class.java)
                .putExtra(EXTRA_BOARD, boardCode)
                .putExtra(EXTRA_IDX, idx)
    }
}
