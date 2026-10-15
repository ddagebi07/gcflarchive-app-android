package kr.co.gcflarchive.app.community

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.co.gcflarchive.app.R
import kr.co.gcflarchive.app.auth.LoginActivity
import kr.co.gcflarchive.app.databinding.ActivityCommunityBinding
import kr.co.gcflarchive.app.databinding.ItemCommunityPostBinding

/** 교내 커뮤니티: 게시판·정렬은 드롭다운으로 고르고, 글은 끝까지 내리면 더 불러와요. */
class CommunityActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCommunityBinding
    private var boards: List<CommunityBoard> = emptyList()
    private var board: CommunityBoard? = null
    private var sort = CommunitySort.NEW
    private var posts = mutableListOf<CommunityPostSummary>()
    private var page = 0
    private var totalPages = 1
    private var loadJob: Job? = null
    private val adapter = Adapter { startActivity(CommunityPostActivity.intent(this, it.id)) }

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) loadBoards()
    }

    private val writer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getIntExtra(CommunityWriteActivity.RESULT_POST_ID, 0) ?: 0
        if (result.resultCode == Activity.RESULT_OK) {
            reload()
            if (id > 0) startActivity(CommunityPostActivity.intent(this, id))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener { finish() }

        val layout = LinearLayoutManager(this)
        binding.list.layoutManager = layout
        binding.list.adapter = adapter
        binding.list.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL).apply {
                dividerColor = getColor(R.color.krds_border)
                isLastItemDecorated = false
            },
        )
        binding.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && layout.findLastVisibleItemPosition() >= posts.size - 4) loadMore()
            }
        })

        binding.sortPicker.setSimpleItems(CommunitySort.entries.map { it.label }.toTypedArray())
        binding.sortPicker.setText(sort.label, false)
        binding.sortPicker.setOnItemClickListener { _, _, position, _ ->
            sort = CommunitySort.entries[position]
            reload()
        }
        binding.boardPicker.setOnItemClickListener { _, _, position, _ ->
            board = boards[position]
            renderBoard()
            reload()
        }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) reload()
            actionId == EditorInfo.IME_ACTION_SEARCH
        }
        binding.swipe.setOnRefreshListener { if (boards.isEmpty()) loadBoards() else reload() }
        binding.btnWrite.setOnClickListener {
            board?.let { writer.launch(CommunityWriteActivity.newPost(this, it.id)) }
        }
        loadBoards()
    }

    override fun onRestart() {
        super.onRestart()
        // Counts (추천·댓글) may have changed on the post screen.
        if (board != null) reload()
    }

    private fun loadBoards() {
        binding.swipe.isRefreshing = true
        showMessage(null)
        lifecycleScope.launch {
            val result = runCatching { CommunityRepository.boards() }
            binding.swipe.isRefreshing = false
            result.onSuccess { list ->
                boards = list
                if (list.isEmpty()) {
                    // Boards are only listed to people who may read them: logged out sees none.
                    val me = runCatching { CommunityRepository.me() }.getOrNull()
                    if (me != null && !me.loggedIn) showLogin() else showMessage(getString(R.string.community_no_boards))
                    return@onSuccess
                }
                binding.boardPicker.setAdapter(ArrayAdapter(this@CommunityActivity, R.layout.item_dropdown, list.map { it.name }))
                board = list.firstOrNull { it.id == board?.id } ?: list.first()
                renderBoard()
                reload()
            }.onFailure(::showError)
        }
    }

    private fun renderBoard() {
        val b = board ?: return
        binding.boardPicker.setText(b.name, false)
        binding.boardDesc.text = b.description.ifBlank { getString(R.string.community_desc) }
        binding.btnWrite.isVisible = b.canWrite
    }

    private fun reload() {
        page = 0
        totalPages = 1
        posts = mutableListOf()
        adapter.submit(posts)
        loadJob?.cancel()
        loadJob = null
        loadMore()
    }

    private fun loadMore() {
        val b = board ?: return
        if (loadJob?.isActive == true || page >= totalPages) return
        val next = page + 1
        if (next == 1) binding.swipe.isRefreshing = true
        showMessage(null)
        loadJob = lifecycleScope.launch {
            val query = binding.searchInput.text?.toString().orEmpty().trim()
            val result = runCatching { CommunityRepository.posts(b.id, next, sort, query) }
            binding.swipe.isRefreshing = false
            result.onSuccess { p ->
                page = p.page
                totalPages = p.totalPages
                posts.addAll(p.posts)
                adapter.submit(posts)
                binding.total.text = getString(R.string.community_total, p.totalCount)
                if (posts.isEmpty()) showMessage(getString(if (query.isBlank()) R.string.community_empty else R.string.community_no_results))
            }.onFailure(::showError)
        }
    }

    private fun showError(e: Throwable) {
        if (e is CancellationException) throw e
        binding.swipe.isRefreshing = false
        if (e is CommunityException && e.loginRequired) return showLogin()
        showMessage(
            (e as? CommunityException)?.message ?: getString(R.string.network_error),
            getString(R.string.retry),
        ) { if (boards.isEmpty()) loadBoards() else reload() }
    }

    private fun showLogin() {
        binding.btnWrite.isVisible = false
        showMessage(getString(R.string.community_login_desc), getString(R.string.login_action)) {
            login.launch(LoginActivity.intent(this))
        }
    }

    private fun showMessage(text: String?, button: String? = null, onClick: (() -> Unit)? = null) {
        binding.messageGroup.isVisible = text != null
        binding.message.text = text
        binding.btnMessage.isVisible = button != null
        binding.btnMessage.text = button
        binding.btnMessage.setOnClickListener { onClick?.invoke() }
    }

    private class Adapter(private val onClick: (CommunityPostSummary) -> Unit) : RecyclerView.Adapter<Adapter.Holder>() {
        private var items: List<CommunityPostSummary> = emptyList()

        @Suppress("NotifyDataSetChanged")
        fun submit(list: List<CommunityPostSummary>) {
            items = list.toList()
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemCommunityPostBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val p = items[position]
            val ctx = holder.b.root.context
            holder.b.title.text = p.title
            holder.b.title.setTextColor(ctx.getColor(if (p.blinded) R.color.krds_text_secondary else R.color.krds_text_strong))
            holder.b.meta.text = listOfNotNull(
                p.author.ifBlank { null },
                CommunityParser.displayTime(p.createdAt).ifBlank { null },
                ctx.getString(R.string.community_likes, p.likeCount),
                ctx.getString(R.string.community_comments, p.commentCount),
                if (p.imageCount > 0) ctx.getString(R.string.community_photos_count, p.imageCount) else null,
                if (p.hasPoll) ctx.getString(R.string.community_poll_badge) else null,
            ).joinToString(" · ")
            holder.b.root.setOnClickListener { onClick(p) }
        }

        class Holder(val b: ItemCommunityPostBinding) : RecyclerView.ViewHolder(b.root)
    }

    companion object {
        fun intent(context: Context) = Intent(context, CommunityActivity::class.java)
    }
}
