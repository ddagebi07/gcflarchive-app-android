package kr.co.gcflarchive.app.archive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ActivityPostDetailBinding
import kr.co.gcflarchive.app.databinding.ItemAttachmentBinding
import kr.co.gcflarchive.app.html.HtmlRenderer
import kr.co.gcflarchive.app.util.Links

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
        HtmlRenderer.render(binding.content, p.contentHtml, baseUrl, lifecycleScope)

        binding.attachmentsBox.isVisible = p.attachments.isNotEmpty()
        binding.attachmentsHeader.text = getString(R.string.post_attachments_count, p.attachments.size)
        binding.attachments.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (a in p.attachments) {
            val row = ItemAttachmentBinding.inflate(inflater, binding.attachments, false)
            row.name.text = a.filename
            row.size.isVisible = a.fileSize > 0
            row.size.text = Formatter.formatShortFileSize(this, a.fileSize)
            row.root.setOnClickListener {
                Links.download(this, a.downloadUrl, fileName = a.filename)
            }
            binding.attachments.addView(row.root)
        }

        bindNav(binding.prevRow, binding.prevTitle, p.prev, R.string.post_prev_none)
        bindNav(binding.nextRow, binding.nextTitle, p.next, R.string.post_next_none)
        binding.navGroup.isVisible = p.prev != null || p.next != null

        binding.btnOriginal.isVisible = p.originalUrl != null
        binding.btnOriginal.setOnClickListener { p.originalUrl?.let { Links.openExternal(this, Uri.parse(it)) } }
    }

    private fun bindNav(row: View, title: TextView, ref: PostRef?, emptyRes: Int) {
        row.isEnabled = ref != null
        title.text = ref?.title ?: getString(emptyRes)
        title.setTextColor(getColor(if (ref != null) R.color.krds_text_primary else R.color.krds_text_disabled))
        row.setOnClickListener {
            ref ?: return@setOnClickListener
            idx = ref.idx
            load()
        }
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
            post?.originalUrl?.let { Links.openExternal(this, Uri.parse(it)) }
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

    companion object {
        private const val EXTRA_BOARD = "board"
        private const val EXTRA_IDX = "idx"

        fun intent(context: Context, boardCode: String, idx: String): Intent =
            Intent(context, PostDetailActivity::class.java)
                .putExtra(EXTRA_BOARD, boardCode)
                .putExtra(EXTRA_IDX, idx)
    }
}
