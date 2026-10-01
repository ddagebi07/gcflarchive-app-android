package kr.co.gcflarchive.app.notice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ActivityNoticeDetailBinding
import kr.co.gcflarchive.app.html.HtmlRenderer
import kr.co.gcflarchive.app.util.Links

/** One 공지사항 (notice-view.html), body rendered natively from its HTML. */
class NoticeDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNoticeDetailBinding
    private var noticeId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoticeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.notice_title)
        binding.toolbar.setNavigationOnClickListener { finish() }
        noticeId = intent.getIntExtra(EXTRA_ID, 0)
        binding.btnRetry.setOnClickListener { load() }
        load()
    }

    private fun load() {
        binding.progress.isVisible = true
        binding.errorGroup.isVisible = false
        binding.body.isVisible = false
        lifecycleScope.launch {
            val result = runCatching { NoticeRepository.detail(noticeId) }
            binding.progress.isVisible = false
            result.onSuccess { n ->
                binding.body.isVisible = true
                binding.critical.isVisible = n.critical
                binding.title.text = n.title
                binding.meta.text = getString(R.string.notice_meta, n.author, n.date, n.views)
                HtmlRenderer.render(binding.content, n.contentHtml, Config.BASE_URL + "/", lifecycleScope)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                binding.errorGroup.isVisible = true
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, R.id.action_share, 0, R.string.share).setIcon(R.drawable.ic_link)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_share -> {
            Links.share(this, "${binding.title.text}\n${Config.url("/notice-view?id=$noticeId")}")
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        private const val EXTRA_ID = "id"

        fun intent(context: Context, id: Int) = Intent(context, NoticeDetailActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
