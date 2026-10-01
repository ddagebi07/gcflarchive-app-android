package kr.co.gcflarchive.app.notice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.databinding.ActivityNoticeListBinding
import kr.co.gcflarchive.app.databinding.ItemNoticeBinding

/** 공지사항 board (notice.html): search + list, newest first as the server returns it. */
class NoticeListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNoticeListBinding
    private var all: List<Notice> = emptyList()
    private val adapter = Adapter { startActivity(NoticeDetailActivity.intent(this, it.id)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoticeListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.list.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL).apply {
                dividerColor = getColor(R.color.krds_border)
                isLastItemDecorated = false
            },
        )
        binding.searchInput.doAfterTextChanged { render() }
        binding.swipe.setOnRefreshListener { load() }
        binding.btnRetry.setOnClickListener { load() }
        load()
    }

    private fun load() {
        binding.swipe.isRefreshing = true
        binding.errorGroup.isVisible = false
        lifecycleScope.launch {
            val result = runCatching { NoticeRepository.list() }
            binding.swipe.isRefreshing = false
            result.onSuccess {
                all = it
                render()
            }.onFailure { e ->
                if (e is CancellationException) throw e
                binding.errorGroup.isVisible = true
                binding.empty.isVisible = false
            }
        }
    }

    private fun render() {
        val items = NoticeLogic.filter(all, binding.searchInput.text?.toString().orEmpty())
        binding.total.text = getString(R.string.library_total, items.size)
        binding.empty.isVisible = items.isEmpty() && !binding.errorGroup.isVisible
        adapter.submit(items)
    }

    private class Adapter(private val onClick: (Notice) -> Unit) : RecyclerView.Adapter<Adapter.Holder>() {
        private var items: List<Notice> = emptyList()

        @Suppress("NotifyDataSetChanged")
        fun submit(list: List<Notice>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemNoticeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val n = items[position]
            val b = holder.b
            b.critical.isVisible = n.critical
            b.title.text = n.title
            b.meta.text = b.root.context.getString(R.string.notice_meta, n.author, n.date, n.views)
            b.root.setOnClickListener { onClick(n) }
        }

        class Holder(val b: ItemNoticeBinding) : RecyclerView.ViewHolder(b.root)
    }

    companion object {
        fun intent(context: Context) = Intent(context, NoticeListActivity::class.java)
    }
}
